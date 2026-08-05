package com.coffee.module.auth.biz.domain;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.temporal.ChronoUnit;

/**
 * 密码重置令牌领域对象
 */
public class PasswordResetToken {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRE_MINUTES = 30;

    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime expiresAt;
    private boolean used;
    private LocalDateTime createdAt;

    public static PasswordResetToken generate(Long userId) {
        PasswordResetToken t = new PasswordResetToken();
        t.userId = userId;
        t.token = generateTokenString();
        t.expiresAt = LocalDateTime.now().plus(EXPIRE_MINUTES, ChronoUnit.MINUTES);
        t.used = false;
        t.createdAt = LocalDateTime.now();
        return t;
    }

    private static String generateTokenString() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    public void markUsed() {
        this.used = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
