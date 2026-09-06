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

    /** 发送登录或注册用途的邮箱验证码。 */
    /**
     * @return 本次发送成功后的再次发送冷却秒数
     */
    int sendEmailCode(EmailCodeRequest request);

    /** 给已登录顾客发送绑定邮箱验证码。 */
    int sendEmailBindCode(Long userId, String email);

    /** 已注册会员使用邮箱验证码登录。 */
    AuthResponse emailLogin(EmailLoginRequest request);

    /** 邮箱验证码校验通过后创建会员账号。 */
    AuthResponse emailRegister(EmailRegisterRequest request);

    /** 校验绑定用途验证码后绑定邮箱。 */
    AuthResponse bindEmail(Long userId, EmailBindRequest request);

    /** 直接解绑当前顾客邮箱。 */
    AuthResponse unbindEmail(Long userId);

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
