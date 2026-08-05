package com.coffee.module.store.api.dto;

/**
 * 店铺响应
 */
public class StoreResponse {
    private Long storeId;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private StoreStatus status;
    private Long merchantId;

    public static StoreResponse from(Long storeId, String code, String name, String address,
                                     String phone, String businessHours,
                                     StoreStatus status, Long merchantId) {
        StoreResponse r = new StoreResponse();
        r.storeId = storeId;
        r.code = code;
        r.name = name;
        r.address = address;
        r.phone = phone;
        r.businessHours = businessHours;
        r.status = status;
        r.merchantId = merchantId;
        return r;
    }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
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
