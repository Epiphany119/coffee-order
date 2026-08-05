package com.coffee.module.auth.biz.domain.repository;

import com.coffee.module.auth.biz.domain.User;

/**
 * 用户仓储接口
 */
public interface UserRepository {
    User findById(Long id);
    User findByUsername(String username);
    boolean existsByUsername(String username);
    User save(User user);
    void updatePassword(Long id, String passwordHash);
}
