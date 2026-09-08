package com.coffee.web.controller;

import com.coffee.common.core.result.Result;
import com.coffee.web.security.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate jdbcTemplate;
    public NotificationController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @GetMapping("/user/{userId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Long userId) {
        AccessGuard.requireUser(userId);
        return Result.success(jdbcTemplate.queryForList("SELECT id, order_id AS orderId, type, title, content, read_status AS readStatus, created_at AS createdAt FROM user_notification WHERE user_id = ? ORDER BY id DESC LIMIT 50", userId));
    }
}
