package com.coffee.module.auth.api;

import com.coffee.module.auth.api.dto.*;

/**
 * 认证服务 API
 */
public interface AuthService {

    /**
     * 用户注册（密码自动 BCrypt 加密）
     */
    AuthResponse register(RegisterRequest request);

    /**
     * 用户登录（支持旧明文密码自动升级为 BCrypt）
     */
    AuthResponse login(LoginRequest request);

    /**
     * 获取用户信息
     */
    AuthResponse getUserInfo(Long id);

    /** 更新顾客个人资料。 */
    AuthResponse updateProfile(Long id, UserProfileUpdateRequest request);

    /** 更新顾客头像地址。 */
    AuthResponse updateAvatar(Long id, String avatarUrl);

    /**
     * 忘记密码 — 生成重置令牌；由 Web 层根据环境策略决定是否展示
     */
    String forgotPassword(ForgotPasswordRequest request);

    /**
     * 重置密码 — 凭令牌设置新密码
     */
    AuthResponse resetPassword(ResetPasswordRequest request);

    /**
     * 获取用户上次选择的店铺 ID（无记录返回 null）
     */
    Long getLastStoreId(Long userId);

    /**
     * 保存用户店铺偏好
     */
    void updateLastStore(Long userId, Long storeId);
}
