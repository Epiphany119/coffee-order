package com.coffee.module.auth.api.dto;

/**
 * 忘记密码请求（申请重置令牌）
 */
public class ForgotPasswordRequest {
    private String username;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
