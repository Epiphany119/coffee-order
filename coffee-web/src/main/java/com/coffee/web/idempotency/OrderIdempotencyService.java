package com.coffee.web.idempotency;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.dto.CreateOrderCommand;
import com.coffee.module.order.api.dto.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 创建订单的持久化幂等控制。
 *
 * <p>同一身份域内的 Idempotency-Key 只能绑定一份请求体和一份成功响应：
 * 重试返回原响应；相同 key 携带不同请求直接拒绝；正在执行的请求返回 409。
 * 记录落在 MySQL 而非 JVM 内存，应用重启后仍可防止重复提交。</p>
 */
@Service
public class OrderIdempotencyService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final String SCOPE_PREFIX = "CREATE_ORDER:";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrderIdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse execute(String idempotencyKey, Long userId, String guestId,
                                 CreateOrderCommand command, Supplier<OrderResponse> creator) {
        if (!isValidKey(idempotencyKey)) {
            throw new ServiceException(400, "Idempotency-Key 格式无效");
        }
        String scope = SCOPE_PREFIX + (userId != null ? "USER:" + userId : "GUEST:" + guestId);
        String requestHash = requestHash(command);
        ExistingRecord existing = claimOrFind(scope, idempotencyKey, requestHash);
        if (existing != null) {
            if (!requestHash.equals(existing.requestHash())) {
                throw new ServiceException(409, "该 Idempotency-Key 已用于另一笔订单");
            }
            if ("SUCCESS".equals(existing.status())) {
                return readResponse(existing.responseBody());
            }
            throw new ServiceException(409, "订单正在创建中，请勿重复提交");
        }

        try {
            OrderResponse response = creator.get();
            jdbcTemplate.update("UPDATE request_idempotency SET status = 'SUCCESS', response_body = ?, updated_at = ? "
                            + "WHERE scope = ? AND idempotency_key = ? AND status = 'PROCESSING'",
                    writeResponse(response), LocalDateTime.now(), scope, idempotencyKey);
            return response;
        } catch (RuntimeException exception) {
            // 业务异常不占用 key，修正请求后仍允许使用同一个 key 重新提交。
            jdbcTemplate.update("DELETE FROM request_idempotency WHERE scope = ? AND idempotency_key = ? AND status = 'PROCESSING'",
                    scope, idempotencyKey);
            throw exception;
        }
    }

    private ExistingRecord claimOrFind(String scope, String key, String requestHash) {
        try {
            jdbcTemplate.update("INSERT INTO request_idempotency (scope, idempotency_key, request_hash, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'PROCESSING', ?, ?)",
                    scope, key, requestHash, LocalDateTime.now(), LocalDateTime.now());
            return null;
        } catch (DuplicateKeyException ignored) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT request_hash, status, response_body FROM request_idempotency WHERE scope = ? AND idempotency_key = ?",
                    scope, key);
            if (rows.isEmpty()) {
                // 极短的并发删除窗口：客户端可安全地重新提交。
                throw new ServiceException(409, "订单请求状态变化中，请稍后重试");
            }
            Map<String, Object> row = rows.get(0);
            return new ExistingRecord(String.valueOf(row.get("request_hash")), String.valueOf(row.get("status")),
                    row.get("response_body") == null ? null : String.valueOf(row.get("response_body")));
        }
    }

    private String requestHash(CreateOrderCommand command) {
        try {
            return sha256(objectMapper.writeValueAsBytes(command));
        } catch (JsonProcessingException e) {
            throw new ServiceException(500, "无法计算订单请求指纹");
        }
    }

    private OrderResponse readResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ServiceException(409, "订单请求正在完成，请稍后重试");
        }
        try {
            return objectMapper.readValue(responseBody, OrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(500, "订单幂等响应读取失败");
        }
    }

    private String writeResponse(OrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new ServiceException(500, "订单幂等响应写入失败");
        }
    }

    static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private record ExistingRecord(String requestHash, String status, String responseBody) { }
}
