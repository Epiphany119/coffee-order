package com.coffee.module.store.api.dto;

/**
 * 商家登录请求（商家编号 sj-xxx + 密码）
 */
public class MerchantLoginRequest {
    private String merchantNo;
    private String password;
    private String challengeId;
    private String challengeCode;

    public String getMerchantNo() { return merchantNo; }
    public void setMerchantNo(String merchantNo) { this.merchantNo = merchantNo; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public String getChallengeCode() { return challengeCode; }
    public void setChallengeCode(String challengeCode) { this.challengeCode = challengeCode; }
}
