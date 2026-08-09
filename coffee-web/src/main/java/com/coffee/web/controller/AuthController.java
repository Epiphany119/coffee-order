package com.coffee.web.controller;

import com.coffee.module.auth.api.AuthService;
import com.coffee.module.auth.api.dto.*;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.TokenService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器（注册/登录/忘记密码/重置密码）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return withToken(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return withToken(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String token = authService.forgotPassword(request);
        return Map.of("success", true, "message", "重置令牌已生成", "token", token);
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/user/{id}")
    public AuthResponse getUserInfo(@PathVariable Long id) {
        AccessGuard.requireUser(id);
        return withToken(authService.getUserInfo(id));
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
