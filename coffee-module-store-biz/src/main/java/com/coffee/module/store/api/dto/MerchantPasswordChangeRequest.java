package com.coffee.module.store.api.dto;

/** 商家密码修改请求。 */
public class MerchantPasswordChangeRequest {
    private String oldPassword;
    private String newPassword;
    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
