package com.coffee.module.store.api.dto;

/**
 * 商家注册请求
 * <p>username：用户端账号（coffee_user.username），须为已注册用户且未注册过商家；
 * 密码默认与用户端登录密码一致（校验一致性后沿用同一密码哈希）
 * <p>商家编号 sj-xxx 由服务端自动生成，作为商家登录账号
 * <p>storeId：入驻现有店铺时必填（激活该店预分配的占位商家记录并绑定）；
 * 为空 = 开新店模式（生成独立商家档案，之后可开店/入驻）
 */
public class MerchantRegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private Long storeId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
}
