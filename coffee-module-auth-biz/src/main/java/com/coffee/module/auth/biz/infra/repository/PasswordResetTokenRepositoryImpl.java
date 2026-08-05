package com.coffee.module.auth.biz.infra.repository;

import com.coffee.module.auth.biz.domain.PasswordResetToken;
import com.coffee.module.auth.biz.domain.repository.PasswordResetTokenRepository;
import com.coffee.module.auth.biz.infra.persistence.PasswordResetTokenMapper;
import com.coffee.module.auth.biz.infra.persistence.PasswordResetTokenPO;
import org.springframework.stereotype.Repository;

/**
 * 密码重置令牌仓储实现
 */
@Repository
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenMapper tokenMapper;

    public PasswordResetTokenRepositoryImpl(PasswordResetTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenPO po = toPO(token);
        if (token.getId() == null) {
            tokenMapper.insert(po);
            token.setId(po.getId());
        } else {
            tokenMapper.updateById(po);
        }
        return token;
    }

    @Override
    public PasswordResetToken findByToken(String token) {
        PasswordResetTokenPO po = tokenMapper.selectByToken(token);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public void invalidatePreviousTokens(Long userId) {
        tokenMapper.invalidateByUserId(userId);
    }

    private PasswordResetToken toDomain(PasswordResetTokenPO po) {
        PasswordResetToken t = new PasswordResetToken();
        t.setId(po.getId());
        t.setUserId(po.getUserId());
        t.setToken(po.getToken());
        t.setExpiresAt(po.getExpiresAt());
        t.setUsed(po.getUsed() != null && po.getUsed());
        t.setCreatedAt(po.getCreatedAt());
        return t;
    }

    private PasswordResetTokenPO toPO(PasswordResetToken t) {
        PasswordResetTokenPO po = new PasswordResetTokenPO();
        po.setId(t.getId());
        po.setUserId(t.getUserId());
        po.setToken(t.getToken());
        po.setExpiresAt(t.getExpiresAt());
        po.setUsed(t.isUsed());
        return po;
    }
}
