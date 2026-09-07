package com.coffee.module.auth.api.dto;

/** 先完成邮箱验证码校验，再创建会员账号；用户名由用户设置，系统账号号码由系统生成。 */
public class EmailRegisterRequest {
    private String email;
    private String code;
    private String username;
    private String password;
    private String nickname;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
