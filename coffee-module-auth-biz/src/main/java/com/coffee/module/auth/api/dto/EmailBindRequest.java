package com.coffee.module.auth.api.dto;

/** 通过一次性验证码绑定顾客邮箱。 */
public class EmailBindRequest {
    private String email;
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
