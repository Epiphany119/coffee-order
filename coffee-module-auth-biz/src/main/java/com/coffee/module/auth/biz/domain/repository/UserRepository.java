package com.coffee.module.auth.biz.domain.repository;

import com.coffee.module.auth.biz.domain.User;

/**
 * 用户仓储接口
 */
public interface UserRepository {
    User findById(Long id);
    User findByUsername(String username);
    User findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    User save(User user);
    void updatePassword(Long id, String passwordHash);
    void updateLastStore(Long id, Long storeId);
    void updateProfile(User user);

    void updateEmail(Long id, String email);
    void updateAvatar(Long id, String avatarUrl);
}
