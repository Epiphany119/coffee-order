package com.coffee.module.store.api.dto;

/**
 * 店铺创建/更新请求
 */
public class StoreRequest {
    /** 店铺编码（创建必填，更新不可改） */
    private String code;
    /** 店名（必填） */
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    /** 营业状态（仅更新时生效） */
    private StoreStatus status;
    /** 绑定商家 id（仅创建时生效） */
    private Long merchantId;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }
    public StoreStatus getStatus() { return status; }
    public void setStatus(StoreStatus status) { this.status = status; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
}
