package com.coffee.module.auth.biz.application.service;

import com.coffee.module.auth.api.AuthService;
import com.coffee.module.auth.api.dto.*;
import com.coffee.module.auth.biz.domain.PasswordResetToken;
import com.coffee.module.auth.biz.domain.User;
import com.coffee.module.auth.biz.domain.repository.PasswordResetTokenRepository;
import com.coffee.module.auth.biz.domain.repository.UserRepository;
import com.coffee.module.auth.biz.domain.service.PasswordValidator;
import com.coffee.module.auth.biz.infra.repository.UserRepositoryImpl;
import com.coffee.module.auth.biz.infra.security.PasswordEncoder;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务
 * <p>
 * 安全特性：
 * 1. 注册：BCrypt(12) 哈希存储密码
 * 2. 登录：验证 BCrypt 哈希；旧明文密码自动升级为 BCrypt
 * 3. 忘记密码：生成 30 分钟有效的一次性令牌
 * 4. 重置密码：令牌校验后允许设置新密码，旧令牌即时作废
 */
@Service
public class AuthApplicationService implements AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;

    public AuthApplicationService(UserRepository userRepository,
                                  PasswordResetTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    // ======================== 注册 ========================

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername();
        String rawPassword = request.getPassword();

        if (username == null || username.isBlank()) {
            return AuthResponse.fail("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            return AuthResponse.fail("密码不能为空");
        }
        try {
            PasswordValidator.validate(rawPassword);
        } catch (IllegalArgumentException e) {
            return AuthResponse.fail(e.getMessage());
        }
        if (userRepository.existsByUsername(username)) {
            return AuthResponse.fail("用户名已存在");
        }

        String hash = PasswordEncoder.encode(rawPassword);
        User user = User.register(username, hash, request.getNickname());
        userRepository.save(user);
        return AuthResponse.ok(user.getId(), user.getUsername(), user.getNickname(),
                user.getTotalSpent() != null ? user.getTotalSpent() : 0.0);
    }

    // ======================== 登录 ========================

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return AuthResponse.fail("用户名或密码错误");
        }

        User user = userRepository.findByUsername(request.getUsername());
        if (user == null) {
            return AuthResponse.fail("用户名或密码错误");
        }

        boolean matched = verifyPassword(request.getPassword(), user);
        if (!matched) {
            return AuthResponse.fail("用户名或密码错误");
        }

        return AuthResponse.ok(user.getId(), user.getUsername(), user.getNickname(),
                user.getTotalSpent() != null ? user.getTotalSpent() : 0.0);
    }

    /**
     * 验密：先 BCrypt，失败则尝试明文（旧用户），比对成功则自动升级
     */
    private boolean verifyPassword(String rawPassword, User user) {
        String stored = user.getPasswordHash();
        if (stored == null) {
            return false;
        }

        // 1) BCrypt 密文（以 $2a$ / $2b$ / $2y$ 开头）
        if (stored.startsWith("$2")) {
            return PasswordEncoder.matches(rawPassword, stored);
        }

        // 2) 旧明文密码：比对后自动升级
        if (stored.equals(rawPassword)) {
            String newHash = PasswordEncoder.encode(rawPassword);
            userRepository.updatePassword(user.getId(), newHash);
            user.setPasswordHash(newHash);
            return true;
        }

        return false;
    }

    // ======================== 用户信息 ========================

    @Override
    public AuthResponse getUserInfo(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            return AuthResponse.fail("用户不存在");
        }
        return AuthResponse.ok(user.getId(), user.getUsername(), user.getNickname(),
                user.getTotalSpent() != null ? user.getTotalSpent() : 0.0);
    }

    // ======================== 店铺偏好 ========================

    @Override
    public Long getLastStoreId(Long userId) {
        User user = userRepository.findById(userId);
        return user != null ? user.getLastStoreId() : null;
    }

    @Override
    public void updateLastStore(Long userId, Long storeId) {
        if (userId != null) {
            userRepository.updateLastStore(userId, storeId);
        }
    }

    // ======================== 忘记密码 ========================

    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            throw new ServiceException(400, "请输入用户名");
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            // 不暴露用户是否存在，统一返回成功
            return "如果该账号存在，重置链接已生成（令牌有效期 30 分钟）";
        }

        // 作废该用户之前所有未使用的令牌
        tokenRepository.invalidatePreviousTokens(user.getId());

        // 生成新令牌
        PasswordResetToken token = PasswordResetToken.generate(user.getId());
        tokenRepository.save(token);

        return token.getToken();
    }

    // ======================== 重置密码 ========================

    @Override
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        String tokenStr = request.getToken();
        String newPassword = request.getNewPassword();

        if (tokenStr == null || tokenStr.isBlank()) {
            return AuthResponse.fail("令牌不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return AuthResponse.fail("新密码不能为空");
        }
        try {
            PasswordValidator.validate(newPassword);
        } catch (IllegalArgumentException e) {
            return AuthResponse.fail(e.getMessage());
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(tokenStr);
        if (resetToken == null || !resetToken.isValid()) {
            return AuthResponse.fail("令牌无效或已过期");
        }

        // 更新密码
        String newHash = PasswordEncoder.encode(newPassword);
        userRepository.updatePassword(resetToken.getUserId(), newHash);

        // 标记令牌已使用
        resetToken.markUsed();
        tokenRepository.save(resetToken);

        // 获取用户信息用于响应
        User user = userRepository.findById(resetToken.getUserId());

        return AuthResponse.ok(resetToken.getUserId(),
                user != null ? user.getUsername() : "",
                user != null ? user.getNickname() : "",
                user != null && user.getTotalSpent() != null ? user.getTotalSpent() : 0.0);
    }
}
