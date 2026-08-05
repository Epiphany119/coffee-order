package com.coffee.web.controller;

import com.coffee.module.auth.api.AuthService;
import com.coffee.module.auth.api.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器（注册/登录/忘记密码/重置密码）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
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
        return authService.getUserInfo(id);
    }
}
