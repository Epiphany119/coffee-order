package com.coffee.module.auth.biz.domain.repository;

import java.util.List;

/** 顾客邮箱绑定关系仓储；邮箱全局唯一，一个顾客最多绑定三个邮箱。 */
public interface UserEmailRepository {
    UserEmailBinding findByEmail(String email);

    List<String> findEmailsByUserId(Long userId);

    int countByUserId(Long userId);

    /** 锁住用户主记录，保证“最多 3 个邮箱”的检查和写入在同一事务内串行化。 */
    void lockUser(Long userId);

    void add(Long userId, String email, boolean primary);

    void delete(Long userId, String email);

    void setPrimary(Long userId, String email);

    record UserEmailBinding(Long userId, String email, boolean primary) {}
}
