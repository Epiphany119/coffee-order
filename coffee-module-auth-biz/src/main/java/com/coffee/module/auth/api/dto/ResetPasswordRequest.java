package com.coffee.module.auth.api.dto;

/**
 * 重置密码请求（提交令牌+新密码）
 */
public class ResetPasswordRequest {
    private String token;
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
