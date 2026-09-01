package com.coffee.module.store.api.dto;

/**
 * 商家响应（注册/登录/信息通用）
 */
public class MerchantResponse {
    private boolean success;
    private String message;
    private Long id;
    /** 商家编号 sj-开头（登录账号） */
    private String merchantNo;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private String operatorName;
    private String email;
    private String businessLicenseNo;
    private String businessLicenseUrl;
    private String otherInfo;
    /** 绑定的店名（入驻后非空） */
    private String storeName;
    private MerchantStatus status;
    /** Bearer 会话令牌；仅注册/登录成功时返回。 */
    private String accessToken;

    public MerchantResponse() {}

    public MerchantResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static MerchantResponse ok(Long id, String merchantNo, String nickname,
                                      String phone, MerchantStatus status, String storeName) {
        MerchantResponse r = new MerchantResponse();
        r.success = true;
        r.message = "操作成功";
        r.id = id;
        r.merchantNo = merchantNo;
        r.nickname = nickname;
        r.phone = phone;
        r.status = status;
        r.storeName = storeName;
        return r;
    }

    public static MerchantResponse fail(String message) {
        return new MerchantResponse(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMerchantNo() { return merchantNo; }
    public void setMerchantNo(String merchantNo) { this.merchantNo = merchantNo; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBusinessLicenseNo() { return businessLicenseNo; }
    public void setBusinessLicenseNo(String businessLicenseNo) { this.businessLicenseNo = businessLicenseNo; }
    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
