package com.coffee.module.auth.api.dto;

/**
 * 已登录用户设置或修改登录密码。
 *
 * <p>已有密码时可使用 currentPassword 验证；忘记原密码或首次设置密码时，
 * 使用当前账户已绑定的 email + emailCode 完成身份校验。</p>
 */
public class UserPasswordUpdateRequest {
    private String currentPassword;
    private String email;
    private String emailCode;
    private String newPassword;
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailCode() { return emailCode; }
    public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
