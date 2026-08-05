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
    private MerchantStatus status;

    public MerchantResponse() {}

    public MerchantResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static MerchantResponse ok(Long id, String merchantNo, String nickname,
                                      String phone, MerchantStatus status) {
        MerchantResponse r = new MerchantResponse();
        r.success = true;
        r.message = "操作成功";
        r.id = id;
        r.merchantNo = merchantNo;
        r.nickname = nickname;
        r.phone = phone;
        r.status = status;
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
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
}
