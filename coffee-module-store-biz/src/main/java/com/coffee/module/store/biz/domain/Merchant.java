package com.coffee.module.store.biz.domain;

import com.coffee.module.store.api.dto.MerchantStatus;

import java.time.LocalDateTime;

/**
 * 商家领域对象
 */
public class Merchant {
    private Long id;
    /** 商家编号 sj-开头（登录账号） */
    private String merchantNo;
    private String username;
    private String passwordHash;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private String operatorName;
    private String email;
    private String businessLicenseNo;
    private String businessLicenseUrl;
    private String otherInfo;
    /** 绑定的店名（入驻时冗余写入，容错/展示用） */
    private String storeName;
    /** 绑定的店铺 id */
    private Long storeId;
    private MerchantStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMerchantNo() { return merchantNo; }
    public void setMerchantNo(String merchantNo) { this.merchantNo = merchantNo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
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
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
