package com.coffee.web.controller;

import com.coffee.module.auth.api.AuthService;
import com.coffee.module.auth.api.dto.*;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.LoginChallengeService;
import com.coffee.web.security.TokenService;
import com.coffee.web.storage.LocalProfileImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证控制器（注册/登录/忘记密码/重置密码）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LoginChallengeService loginChallengeService;
    private final LocalProfileImageStorage profileImageStorage;
    private final boolean exposeResetToken;

    public AuthController(AuthService authService, TokenService tokenService, LoginChallengeService loginChallengeService,
                          LocalProfileImageStorage profileImageStorage,
                          @Value("${coffee.auth.expose-reset-token:false}") boolean exposeResetToken) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.loginChallengeService = loginChallengeService;
        this.profileImageStorage = profileImageStorage;
        this.exposeResetToken = exposeResetToken;
    }

    @GetMapping("/login-challenge")
    public LoginChallengeService.LoginChallenge loginChallenge() { return loginChallengeService.issue(); }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return withToken(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        if (!loginChallengeService.verify(request.getChallengeId(), request.getChallengeCode())) {
            return AuthResponse.fail("验证码错误、已过期或已使用，请刷新后重试");
        }
        return withToken(authService.login(request));
    }

    /** 向用户邮箱发送一次性登录或注册验证码。 */
    @PostMapping("/email/send-code")
    public Map<String, Object> sendEmailCode(@RequestBody EmailCodeRequest request) {
        int cooldownSeconds = authService.sendEmailCode(request);
        return Map.of("success", true, "message", "验证码已发送，请查收邮箱", "cooldownSeconds", cooldownSeconds);
    }

    /** 注册或绑定前检查邮箱是否已被其他顾客占用。 */
    @GetMapping("/email/check")
    public EmailAvailabilityResponse checkEmailAvailability(@RequestParam String email) {
        return authService.checkEmailAvailability(email);
    }

    /** 已登录顾客绑定邮箱专用的验证码入口；验证码用途固定为 BIND。 */
    @PostMapping("/user/{id}/email/send-code")
    public Map<String, Object> sendEmailBindCode(@PathVariable Long id,
                                                  @RequestBody EmailCodeRequest request) {
        AccessGuard.requireUser(id);
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        int cooldownSeconds = authService.sendEmailBindCode(id, request.getEmail());
        return Map.of("success", true, "message", "绑定验证码已发送，请查收邮箱", "cooldownSeconds", cooldownSeconds);
    }

    /** 已创建账户的用户可仅凭邮箱验证码登录。 */
    @PostMapping("/email/login")
    public AuthResponse emailLogin(@RequestBody EmailLoginRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return withToken(authService.emailLogin(request));
    }

    /** 使用邮箱验证码创建会员账户。 */
    @PostMapping("/email/register")
    public AuthResponse emailRegister(@RequestBody EmailRegisterRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return withToken(authService.emailRegister(request));
    }

    /** 验证绑定用途验证码后写入当前顾客邮箱。 */
    @PutMapping("/user/{id}/email")
    public AuthResponse bindEmail(@PathVariable Long id, @RequestBody EmailBindRequest request) {
        AccessGuard.requireUser(id);
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return withToken(authService.bindEmail(id, request));
    }

    /** 解绑当前顾客指定邮箱，不需要再次输入验证码；不传 email 时兼容旧客户端。 */
    @DeleteMapping("/user/{id}/email")
    public AuthResponse unbindEmail(@PathVariable Long id,
                                    @RequestParam(required = false) String email) {
        AccessGuard.requireUser(id);
        return withToken(authService.unbindEmail(id, email));
    }

    /** 当前登录用户使用已绑定邮箱完成密码操作的二次身份验证。 */
    @PostMapping("/user/{id}/password/send-code")
    public Map<String, Object> sendPasswordVerificationCode(@PathVariable Long id,
                                                             @RequestBody EmailCodeRequest request) {
        AccessGuard.requireUser(id);
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        int cooldownSeconds = authService.sendPasswordVerificationCode(id, request.getEmail());
        return Map.of("success", true, "message", "身份验证码已发送，请查收邮箱", "cooldownSeconds", cooldownSeconds);
    }

    /** 设置或修改密码；成功后不续签令牌，客户端必须清除当前登录状态。 */
    @PutMapping("/user/{id}/password")
    public AuthResponse updatePassword(@PathVariable Long id,
                                       @RequestBody UserPasswordUpdateRequest request) {
        AccessGuard.requireUser(id);
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return authService.updatePassword(id, request);
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        String token = authService.forgotPassword(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "如果账号存在，重置流程已提交，请通过已配置的找回渠道获取令牌");
        // 只允许本地显式打开，绝不能因为方便联调而默认把高价值凭证返回给匿名调用方。
        if (exposeResetToken && token != null && !token.isBlank()) {
            response.put("message", "开发环境：重置令牌已生成");
            response.put("token", token);
        }
        return response;
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        return authService.resetPassword(request);
    }

    @GetMapping("/user/{id}")
    public AuthResponse getUserInfo(@PathVariable Long id) {
        AccessGuard.requireUser(id);
        return withToken(authService.getUserInfo(id));
    }

    /** 更新顾客个人资料。 */
    @PutMapping("/user/{id}/profile")
    public AuthResponse updateProfile(@PathVariable Long id,
                                      @RequestBody UserProfileUpdateRequest request) {
        AccessGuard.requireUser(id);
        return withToken(authService.updateProfile(id, request));
    }

    /** 上传顾客头像；图片只返回站内相对地址，不把文件内容存进数据库。 */
    @PostMapping("/user/{id}/avatar")
    public AuthResponse uploadAvatar(@PathVariable Long id,
                                     @RequestParam("file") MultipartFile file) {
        AccessGuard.requireUser(id);
        String url = profileImageStorage.store("user", id, file);
        return withToken(authService.updateAvatar(id, url));
    }

    /** 用户店铺偏好：GET /api/auth/user/{id}/preference */
    @GetMapping("/user/{id}/preference")
    public Map<String, Object> getPreference(@PathVariable Long id) {
        AccessGuard.requireUser(id);
        return Map.of("success", true, "lastStoreId", authService.getLastStoreId(id));
    }

    /** 保存店铺偏好：PUT /api/auth/user/{id}/preference  body {"storeId":1} */
    @PutMapping("/user/{id}/preference")
    public Map<String, Object> updatePreference(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AccessGuard.requireUser(id);
        if (body == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        Long storeId = body.get("storeId") == null ? null : Long.valueOf(body.get("storeId").toString());
        authService.updateLastStore(id, storeId);
        return Map.of("success", true, "message", "偏好已保存");
    }

    private AuthResponse withToken(AuthResponse response) {
        if (response != null && response.isSuccess() && response.getId() != null) {
            response.setAccessToken(tokenService.issueUser(response.getId()));
        }
        return response;
    }
}
