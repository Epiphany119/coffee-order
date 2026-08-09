package com.coffee.module.inventory.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.inventory.api.InventoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 库存应用服务：MySQL 条件更新保证原子预扣，Redis 仅作为可失效的读取快照。 */
@Service
public class InventoryApplicationService implements InventoryService {
    private static final int INITIAL_STOCK = 100;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisCacheEnabled;

    public InventoryApplicationService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                                       @Value("${coffee.inventory.redis-cache-enabled:false}") boolean redisCacheEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.redisCacheEnabled = redisCacheEnabled;
    }

    @Override
    public void reserve(Long storeId, Long productId, int quantity) {
        if (storeId == null || productId == null || quantity <= 0) {
            throw new ServiceException(400, "库存预扣参数无效");
        }
        jdbcTemplate.update("INSERT INTO inventory_stock (store_id, product_id, available_stock, locked_stock, version) "
                        + "VALUES (?, ?, ?, 0, 0) ON DUPLICATE KEY UPDATE product_id = VALUES(product_id)",
                storeId, productId, INITIAL_STOCK);
        int changed = jdbcTemplate.update("UPDATE inventory_stock SET available_stock = available_stock - ?, locked_stock = locked_stock + ?, version = version + 1 "
                        + "WHERE store_id = ? AND product_id = ? AND available_stock >= ?",
                quantity, quantity, storeId, productId, quantity);
        if (changed == 0) {
            throw new ServiceException(409, "商品库存不足，请减少数量或换一款试试");
        }
        refreshCache(storeId, productId);
    }

    @Override
    public void release(Long storeId, Long productId, int quantity) {
        if (storeId == null || productId == null || quantity <= 0) return;
        jdbcTemplate.update("UPDATE inventory_stock SET available_stock = available_stock + ?, "
                        + "locked_stock = GREATEST(locked_stock - ?, 0), version = version + 1 "
                        + "WHERE store_id = ? AND product_id = ?",
                quantity, quantity, storeId, productId);
        refreshCache(storeId, productId);
    }

    private void refreshCache(Long storeId, Long productId) {
        if (!redisCacheEnabled) return;
        Integer stock = jdbcTemplate.queryForObject("SELECT available_stock FROM inventory_stock WHERE store_id = ? AND product_id = ?",
                Integer.class, storeId, productId);
        if (stock != null) {
            redisTemplate.opsForValue().set(cacheKey(storeId, productId), String.valueOf(stock), Duration.ofMinutes(10));
        }
    }

    private String cacheKey(Long storeId, Long productId) {
        return "fika:inventory:" + storeId + ':' + productId;
    }
}
