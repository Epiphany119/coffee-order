package com.coffee.module.marketing.biz.application;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.marketing.api.FlashSaleService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

/**
 * 秒杀核心：先落用户抢购记录（唯一键限购），再用条件更新原子扣库存；失败时事务回滚。
 */
@Service
public class FlashSaleApplicationService implements FlashSaleService {
    private static final DefaultRedisScript<Long> CLAIM_LUA = new DefaultRedisScript<>();
    static {
        CLAIM_LUA.setLocation(new ClassPathResource("lua/flash_sale_claim.lua"));
        CLAIM_LUA.setResultType(Long.class);
    }
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;

    public FlashSaleApplicationService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                                       @Value("${coffee.flash-sale.redis-enabled:false}") boolean redisEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
    }

    @Override
    public List<Map<String, Object>> listCurrent(Long storeId) {
        // 已售罄/已结束的活动也要返回给前端，展示为灰态而不是凭空消失。
        return jdbcTemplate.queryForList("SELECT id, product_code AS productCode, title, flash_price AS flashPrice, "
                + "available_stock AS availableStock, start_at AS startAt, end_at AS endAt, "
                + "CASE WHEN enabled = 1 AND NOW() BETWEEN start_at AND end_at AND available_stock > 0 THEN TRUE ELSE FALSE END AS claimable, "
                + "CASE WHEN available_stock <= 0 THEN 'SOLD_OUT' WHEN end_at <= NOW() OR enabled = 0 THEN 'ENDED' ELSE NULL END AS closeReason "
                + "FROM flash_sale_activity WHERE store_id = ? AND start_at <= NOW() "
                + "ORDER BY (enabled = 1 AND NOW() BETWEEN start_at AND end_at AND available_stock > 0) DESC, end_at DESC LIMIT 3", storeId);
    }

    @Override
    @Transactional
    public Map<String, Object> claim(Long activityId, Long userId, String guestId) {
        if (userId == null && (guestId == null || guestId.isBlank())) throw new ServiceException(400, "请先建立用户或游客身份");
        Map<String, Object> activity;
        try {
            activity = jdbcTemplate.queryForMap("SELECT id, product_code AS productCode, title, flash_price AS flashPrice, "
                    + "available_stock AS availableStock FROM flash_sale_activity "
                    + "WHERE id = ? AND enabled = 1 AND NOW() BETWEEN start_at AND end_at", activityId);
        } catch (Exception e) { throw new ServiceException(404, "活动不存在或已结束"); }
        String identity = userId != null ? "u:" + userId : "g:" + guestId;
        String claimNo = "FS" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        boolean redisClaimed = false;
        if (redisEnabled) {
            String stockKey = "fika:flash-sale:stock:" + activityId;
            String userKey = "fika:flash-sale:user:" + activityId + ':' + identity;
            redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(activity.get("availableStock")), Duration.ofMinutes(11));
            Long result = redisTemplate.execute(CLAIM_LUA, List.of(stockKey, userKey), "660");
            if (result == null || result == 0L) throw new ServiceException(409, "手慢了，本场秒杀已售罄");
            if (result == 2L) throw new ServiceException(409, "本场活动每人限购一份");
            redisClaimed = true;
        }
        try {
            jdbcTemplate.update("INSERT INTO flash_sale_claim (activity_id, identity_key, claim_no, status) VALUES (?, ?, ?, 'CLAIMED')", activityId, identity, claimNo);
        } catch (DuplicateKeyException e) {
            if (redisClaimed) compensateRedisClaim(activityId, identity);
            throw new ServiceException(409, "本场活动每人限购一份");
        }
        int changed = jdbcTemplate.update("UPDATE flash_sale_activity SET available_stock = available_stock - 1, sold_stock = sold_stock + 1 "
                + ", enabled = IF(available_stock = 1, 0, enabled) "
                + "WHERE id = ? AND enabled = 1 AND NOW() BETWEEN start_at AND end_at AND available_stock > 0", activityId);
        if (changed == 0) {
            if (redisClaimed) compensateRedisClaim(activityId, identity);
            throw new ServiceException(409, "手慢了，本场秒杀已售罄");
        }
        // 抢购码不能只停留在一次性 toast：登录用户可在会员中心的消息和抢购资格中随时找回。
        if (userId != null) {
            String title = String.valueOf(activity.get("title"));
            jdbcTemplate.update("INSERT INTO user_notification (user_id, type, title, content, read_status, created_at) VALUES (?, 'FLASH_SALE_CLAIM', ?, ?, 0, NOW())",
                    userId, "抢购成功 · 资格已保存",
                    "你已抢到「" + title + "」，抢购码：" + claimNo + "。可在会员中心的“我的抢购”中查看。");
        }
        activity.put("claimNo", claimNo);
        activity.put("message", "抢购成功，抢购资格已保存");
        return activity;
    }

    @Override
    public List<Map<String, Object>> listClaims(Long userId, String guestId) {
        if (userId == null && (guestId == null || guestId.isBlank())) {
            throw new ServiceException(400, "请先建立用户或游客身份");
        }
        String identity = userId != null ? "u:" + userId : "g:" + guestId;
        return jdbcTemplate.queryForList("SELECT c.claim_no AS claimNo, c.status, c.created_at AS claimedAt, "
                + "DATE_ADD(c.created_at, INTERVAL 10 MINUTE) AS expiresAt, a.product_code AS productCode, "
                + "a.title, a.flash_price AS flashPrice FROM flash_sale_claim c "
                + "JOIN flash_sale_activity a ON a.id = c.activity_id "
                + "WHERE c.identity_key = ? ORDER BY c.id DESC LIMIT 50", identity);
    }

    @Override
    @Transactional
    public double consumePrice(String claimNo, String productCode, Long userId, String guestId) {
        String identity = userId != null ? "u:" + userId : "g:" + guestId;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT a.flash_price AS flashPrice FROM flash_sale_claim c "
                + "JOIN flash_sale_activity a ON a.id = c.activity_id WHERE c.claim_no = ? AND c.identity_key = ? "
                + "AND c.status = 'CLAIMED' AND a.product_code = ?", claimNo, identity, productCode);
        if (rows.isEmpty()) throw new ServiceException(409, "秒杀资格无效、已核销或与商品不匹配");
        int changed = jdbcTemplate.update("UPDATE flash_sale_claim SET status = 'USED' WHERE claim_no = ? AND identity_key = ? AND status = 'CLAIMED'", claimNo, identity);
        if (changed != 1) throw new ServiceException(409, "秒杀资格已被核销");
        return ((Number) rows.get(0).get("flashPrice")).doubleValue();
    }

    /**
     * 抢购码只保留 10 分钟。到期时用状态条件更新（CAS）将资格置为 EXPIRED 并归还名额；
     * 和支付核销并发时只有一个状态迁移会成功，避免重复归还库存。
     */
    @Scheduled(fixedDelayString = "${coffee.flash-sale.claim-expire-scan-ms:30000}")
    @Transactional
    public void expireClaimsAndCloseFinishedActivities() {
        List<Map<String, Object>> expired = jdbcTemplate.queryForList("SELECT c.claim_no AS claimNo, c.identity_key AS identityKey, "
                + "c.activity_id AS activityId FROM flash_sale_claim c WHERE c.status = 'CLAIMED' "
                + "AND c.created_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE) LIMIT 200");
        for (Map<String, Object> claim : expired) {
            Long activityId = ((Number) claim.get("activityId")).longValue();
            int changed = jdbcTemplate.update("UPDATE flash_sale_claim c JOIN flash_sale_activity a ON a.id = c.activity_id "
                    + "SET c.status = 'EXPIRED', a.available_stock = a.available_stock + 1, "
                    + "a.sold_stock = GREATEST(a.sold_stock - 1, 0), "
                    + "a.enabled = IF(NOW() < a.end_at, 1, a.enabled) "
                    + "WHERE c.claim_no = ? AND c.status = 'CLAIMED'", claim.get("claimNo"));
            if (changed == 1 && redisEnabled) {
                compensateRedisClaim(activityId, String.valueOf(claim.get("identityKey")));
            }
        }
        // 无论活动是售罄还是自然结束，都保留给前端灰态展示，但不再接受抢购。
        jdbcTemplate.update("UPDATE flash_sale_activity SET enabled = 0 "
                + "WHERE enabled = 1 AND (end_at <= NOW() OR available_stock <= 0)");
    }

    private void compensateRedisClaim(Long activityId, String identity) {
        redisTemplate.opsForValue().increment("fika:flash-sale:stock:" + activityId);
        redisTemplate.delete("fika:flash-sale:user:" + activityId + ':' + identity);
    }
}
