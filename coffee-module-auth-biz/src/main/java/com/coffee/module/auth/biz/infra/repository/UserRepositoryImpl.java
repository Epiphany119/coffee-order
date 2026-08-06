package com.coffee.module.auth.biz.infra.repository;

import com.coffee.module.auth.biz.domain.User;
import com.coffee.module.auth.biz.domain.repository.UserRepository;
import com.coffee.module.auth.biz.infra.persistence.UserMapper;
import com.coffee.module.auth.biz.infra.persistence.UserPO;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findById(Long id) {
        UserPO po = userMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public User findByUsername(String username) {
        UserPO po = userMapper.selectByUsername(username);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    @Override
    public User save(User user) {
        UserPO po = toPO(user);
        if (user.getId() == null) {
            userMapper.insert(po);
            user.setId(po.getId());
        } else {
            userMapper.updateById(po);
        }
        return user;
    }

    @Override
    public void updatePassword(Long id, String passwordHash) {
        userMapper.updatePassword(id, passwordHash);
    }

    @Override
    public void updateLastStore(Long id, Long storeId) {
        userMapper.updateLastStore(id, storeId);
    }

    /**
     * 获取原始密码（用于旧用户明文比对后自动升级）
     */
    String getRawPassword(Long id) {
        return userMapper.selectPasswordById(id);
    }

    private User toDomain(UserPO po) {
        User user = new User();
        user.setId(po.getId());
        user.setUsername(po.getUsername());
        user.setPasswordHash(po.getPassword());
        user.setNickname(po.getNickname());
        user.setTotalSpent(po.getTotalSpent());
        user.setRole(po.getRole());
        user.setLastStoreId(po.getLastStoreId());
        user.setCreatedAt(po.getCreatedAt());
        return user;
    }

    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.setId(user.getId());
        po.setUsername(user.getUsername());
        po.setPassword(user.getPasswordHash());
        po.setNickname(user.getNickname());
        po.setTotalSpent(user.getTotalSpent());
        po.setRole(user.getRole());
        po.setLastStoreId(user.getLastStoreId());
        po.setCreatedAt(user.getCreatedAt());
        return po;
    }
}
