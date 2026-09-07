package com.coffee.module.auth.biz.domain;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户领域对象
 */
public class User {
    private Long id;
    /** 对外展示的 FIKA 账号号码；与数据库自增主键 id 分离。 */
    private String accountNo;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private LocalDate birthday;
    private String wechatId;
    private String qqNumber;
    private String email;
    private List<String> emails = new ArrayList<>();
    private String otherInfo;
    private Double totalSpent;
    private String role;
    private Long lastStoreId;
    private LocalDateTime createdAt;

    public static User register(String username, String passwordHash, String nickname) {
        User user = new User();
        user.username = username;
        user.passwordHash = passwordHash;
        user.nickname = (nickname != null && !nickname.isBlank()) ? nickname : username;
        user.totalSpent = 0.0;
        user.role = "USER";
        user.createdAt = LocalDateTime.now();
        return user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public String getWechatId() { return wechatId; }
    public void setWechatId(String wechatId) { this.wechatId = wechatId; }
    public String getQqNumber() { return qqNumber; }
    public void setQqNumber(String qqNumber) { this.qqNumber = qqNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<String> getEmails() { return emails; }
    public void setEmails(List<String> emails) {
        this.emails = emails == null ? new ArrayList<>() : new ArrayList<>(emails);
    }
    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getLastStoreId() { return lastStoreId; }
    public void setLastStoreId(Long lastStoreId) { this.lastStoreId = lastStoreId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
