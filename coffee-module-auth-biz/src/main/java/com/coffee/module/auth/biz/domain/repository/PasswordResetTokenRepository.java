package com.coffee.module.auth.biz.domain.repository;

import com.coffee.module.auth.biz.domain.PasswordResetToken;

/**
 * 密码重置令牌仓储接口
 */
public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    PasswordResetToken findByToken(String token);
    void invalidatePreviousTokens(Long userId);
}
