package com.coffee.module.store.api.dto;

/**
 * 商家注册请求（商家编号 sj-xxx 由服务端自动生成）
 */
public class MerchantRegisterRequest {
    private String password;
    private String nickname;
    private String phone;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
