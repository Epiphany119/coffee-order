package com.coffee.module.store.api.dto;

/** 商家可自行维护的公开资料。 */
public class MerchantProfileUpdateRequest {
    private String nickname;
    private String phone;
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
