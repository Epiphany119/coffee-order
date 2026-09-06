package com.coffee.module.auth.api.dto;

/** 请求发送邮箱验证码。 */
public class EmailCodeRequest {
    private String email;
    private EmailCodePurpose purpose;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public EmailCodePurpose getPurpose() { return purpose; }
    public void setPurpose(EmailCodePurpose purpose) { this.purpose = purpose; }
}
