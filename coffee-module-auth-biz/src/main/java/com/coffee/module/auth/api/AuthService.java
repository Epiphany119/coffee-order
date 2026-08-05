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

    /**
     * 忘记密码 — 生成重置令牌并返回
     */
    String forgotPassword(ForgotPasswordRequest request);

    /**
     * 重置密码 — 凭令牌设置新密码
     */
    AuthResponse resetPassword(ResetPasswordRequest request);
}
