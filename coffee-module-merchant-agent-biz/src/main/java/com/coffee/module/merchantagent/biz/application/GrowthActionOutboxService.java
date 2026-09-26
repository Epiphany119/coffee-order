package com.coffee.module.merchantagent.biz.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable delivery queue for merchant AI actions. Each target is an independent
 * outbox row, so a failed recipient never causes completed recipients to repeat.
 */
@Service
public class GrowthActionOutboxService {
    private static final Logger log = LoggerFactory.getLogger(GrowthActionOutboxService.class);
    private static final int TARGET_LIMIT = 30;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final int maxAttempts;

    public GrowthActionOutboxService(JdbcTemplate jdbc,
                                     ObjectMapper json,
                                     org.springframework.transaction.PlatformTransactionManager transactionManager,
                                     @Value("${coffee.ai.merchant-outbox.max-attempts:8}") int maxAttempts) {
        this.jdbc = jdbc;
        this.json = json;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20));
    }

    public int enqueue(Long actionId, Long merchantId, Long storeId, String operationType,
                       String title, Map<String, Object> proposal) {
        List<Long> users = users(storeId, intNumber(proposal == null ? null : proposal.get("targetDays"), 30, 90));
        if (users.isEmpty()) return 0;
        try {
            String payload = json.writeValueAsString(proposal == null ? Map.of() : proposal);
            for (Long userId : users) {
                jdbc.update("""
                        INSERT INTO growth_agent_outbox
                            (action_id,merchant_id,store_id,target_user_id,operation_type,title,payload_json,
                             status,attempts,next_attempt_at,created_at)
                        VALUES (?,?,?,?,?,?,?,'PENDING',0,NOW(),NOW())
                        ON DUPLICATE KEY UPDATE id=id
                        """, actionId, merchantId, storeId, userId, operationType, title, payload);
            }
            return users.size();
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建商家 Agent Outbox", ex);
        }
    }

    @Scheduled(fixedDelayString = "${coffee.ai.merchant-outbox.fixed-delay-ms:5000}")
    public void dispatch() {
        try {
            jdbc.update("""
                    UPDATE growth_agent_outbox
                    SET status='PENDING',locked_at=NULL
                    WHERE status='PROCESSING' AND locked_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE)
                    """);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id,action_id,target_user_id,operation_type,title,payload_json,attempts
                    FROM growth_agent_outbox
                    WHERE status='PENDING' AND next_attempt_at <= NOW()
                    ORDER BY id LIMIT 20
                    """);
            for (Map<String, Object> row : rows) dispatchOne(row);
        } catch (RuntimeException ex) {
            log.warn("Merchant growth outbox dispatch failed; it will retry", ex);
        }
    }

    private void dispatchOne(Map<String, Object> row) {
        long id = longNumber(row.get("id"), 0);
        if (jdbc.update("UPDATE growth_agent_outbox SET status='PROCESSING',locked_at=NOW(),attempts=attempts+1 WHERE id=? AND status='PENDING'", id) != 1) {
            return;
        }
        try {
            transaction.executeWithoutResult(status -> deliver(row));
        } catch (RuntimeException ex) {
            int attempts = intNumber(row.get("attempts"), 0, Integer.MAX_VALUE - 1) + 1;
            if (attempts >= maxAttempts) {
                jdbc.update("UPDATE growth_agent_outbox SET status='FAILED',last_error=?,locked_at=NULL WHERE id=?",
                        safeError(ex), id);
            } else {
                long delaySeconds = Math.min(3600, 5L << Math.min(10, Math.max(0, attempts - 1)));
                jdbc.update("UPDATE growth_agent_outbox SET status='PENDING',next_attempt_at=DATE_ADD(NOW(),INTERVAL ? SECOND),last_error=?,locked_at=NULL WHERE id=?",
                        delaySeconds, safeError(ex), id);
            }
            refreshActionStatus(longNumber(row.get("action_id"), 0));
        }
    }

    private void deliver(Map<String, Object> row) {
        long outboxId = longNumber(row.get("id"), 0);
        long actionId = longNumber(row.get("action_id"), 0);
        long userId = longNumber(row.get("target_user_id"), 0);
        String operation = String.valueOf(row.get("operation_type"));
        String title = String.valueOf(row.get("title"));
        Map<String, Object> proposal = readProposal(String.valueOf(row.get("payload_json")));

        jdbc.update("""
                INSERT INTO growth_agent_delivery(outbox_id,action_id,target_user_id,operation_type,status)
                VALUES (?,?,?,?, 'PROCESSING')
                ON DUPLICATE KEY UPDATE outbox_id=VALUES(outbox_id)
                """, outboxId, actionId, userId, operation);
        String deliveryStatus = jdbc.queryForObject(
                "SELECT status FROM growth_agent_delivery WHERE outbox_id=?", String.class, outboxId);
        if ("SUCCEEDED".equals(deliveryStatus)) {
            jdbc.update("UPDATE growth_agent_outbox SET status='SUCCEEDED',executed_at=COALESCE(executed_at,NOW()),locked_at=NULL WHERE id=?", outboxId);
            refreshActionStatus(actionId);
            return;
        }

        if ("CREATE_VOUCHERS".equalsIgnoreCase(operation)) {
            String voucherNo = deterministicVoucherNo(outboxId);
            jdbc.update("""
                    INSERT INTO user_voucher
                        (user_id,voucher_no,name,discount,minimum,status,source,created_at,expires_at)
                    VALUES (?,?,?,?,?,0,'GROWTH_AGENT',NOW(),DATE_ADD(NOW(),INTERVAL ? DAY))
                    ON DUPLICATE KEY UPDATE voucher_no=VALUES(voucher_no)
                    """, userId, voucherNo, title, intNumber(proposal.get("discount"), 8, 100),
                    intNumber(proposal.get("minimum"), 48, 1000), intNumber(proposal.get("expiresDays"), 3, 30));
        }
        jdbc.update("INSERT INTO user_notification (user_id,type,title,content,read_status,created_at) VALUES (?,'GROWTH_AGENT',?,?,0,NOW())",
                userId, title, String.valueOf(proposal.getOrDefault("message", "FIKA 为你准备了一条门店提醒")));
        jdbc.update("UPDATE growth_agent_delivery SET status='SUCCEEDED',executed_at=NOW() WHERE outbox_id=?", outboxId);
        jdbc.update("UPDATE growth_agent_outbox SET status='SUCCEEDED',executed_at=NOW(),locked_at=NULL,last_error=NULL WHERE id=?", outboxId);
        refreshActionStatus(actionId);
    }

    private void refreshActionStatus(long actionId) {
        jdbc.update("""
                UPDATE growth_agent_action
                SET status=CASE
                    WHEN EXISTS (SELECT 1 FROM growth_agent_outbox WHERE action_id=? AND status IN ('PENDING','PROCESSING')) THEN 'EXECUTING'
                    WHEN EXISTS (SELECT 1 FROM growth_agent_outbox WHERE action_id=? AND status='FAILED') THEN 'FAILED'
                    ELSE 'EXECUTED'
                    END,
                    executed_at=CASE WHEN NOT EXISTS (SELECT 1 FROM growth_agent_outbox WHERE action_id=? AND status IN ('PENDING','PROCESSING')) THEN COALESCE(executed_at,NOW()) ELSE executed_at END
                WHERE id=?
                """, actionId, actionId, actionId, actionId);
    }

    private List<Long> users(Long storeId, int days) {
        return jdbc.queryForList("SELECT user_id FROM user_order WHERE store_id=? AND user_id IS NOT NULL AND created_at>=DATE_SUB(NOW(),INTERVAL ? DAY) GROUP BY user_id ORDER BY MAX(id) DESC LIMIT " + TARGET_LIMIT,
                Long.class, storeId, days);
    }

    private Map<String, Object> readProposal(String payload) {
        try { return json.readValue(payload, new TypeReference<>() { }); }
        catch (Exception ex) { return Map.of(); }
    }

    private String deterministicVoucherNo(long outboxId) {
        byte[] hash;
        try { hash = MessageDigest.getInstance("SHA-256").digest(("growth:" + outboxId).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { hash = UUID.nameUUIDFromBytes(String.valueOf(outboxId).getBytes(StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8); }
        return "AG" + HexFormat.of().formatHex(hash).substring(0, 18).toUpperCase();
    }

    private int intNumber(Object value, int fallback, int max) {
        try { return Math.max(1, Math.min(max, Integer.parseInt(String.valueOf(value)))); }
        catch (Exception ex) { return fallback; }
    }

    private long longNumber(Object value, long fallback) {
        try { return Math.max(1L, Long.parseLong(String.valueOf(value))); }
        catch (Exception ex) { return fallback; }
    }

    private String safeError(Exception ex) {
        String value = ex.getMessage();
        return value == null || value.isBlank() ? ex.getClass().getSimpleName() : value.substring(0, Math.min(500, value.length()));
    }
}
