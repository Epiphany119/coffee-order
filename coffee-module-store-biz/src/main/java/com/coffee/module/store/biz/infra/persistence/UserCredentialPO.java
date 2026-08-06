package com.coffee.module.store.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户端账号凭证（coffee_user 表，仅取商家注册校验所需字段）
 * <p>商家注册须关联已注册的用户端账号：用户名存在性 + 密码一致性校验；
 * merchantNo 记录该用户端账号绑定的商家编号（一个账号只能绑定一家店）
 */
@TableName("coffee_user")
public class UserCredentialPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    /** 绑定的商家编号（null = 未入驻） */
    private String merchantNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getMerchantNo() { return merchantNo; }
    public void setMerchantNo(String merchantNo) { this.merchantNo = merchantNo; }
}
