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
    public User findByEmail(String email) {
        UserPO po = userMapper.selectByEmail(email);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.countByEmail(email) > 0;
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

    @Override
    public void updateProfile(User user) {
        userMapper.updateProfile(user.getId(), user.getNickname(), user.getPhone(), user.getBirthday(),
                user.getWechatId(), user.getQqNumber(), user.getOtherInfo());
    }

    @Override
    public void updateEmail(Long id, String email) {
        userMapper.updateEmail(id, email);
    }

    @Override
    public void updateAvatar(Long id, String avatarUrl) {
        userMapper.updateAvatar(id, avatarUrl);
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
        user.setAvatarUrl(po.getAvatarUrl());
        user.setPhone(po.getPhone());
        user.setBirthday(po.getBirthday());
        user.setWechatId(po.getWechatId());
        user.setQqNumber(po.getQqNumber());
        user.setEmail(po.getEmail());
        user.setOtherInfo(po.getOtherInfo());
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
        po.setAvatarUrl(user.getAvatarUrl());
        po.setPhone(user.getPhone());
        po.setBirthday(user.getBirthday());
        po.setWechatId(user.getWechatId());
        po.setQqNumber(user.getQqNumber());
        po.setEmail(user.getEmail());
        po.setOtherInfo(user.getOtherInfo());
        po.setTotalSpent(user.getTotalSpent());
        po.setRole(user.getRole());
        po.setLastStoreId(user.getLastStoreId());
        po.setCreatedAt(user.getCreatedAt());
        return po;
    }
}
