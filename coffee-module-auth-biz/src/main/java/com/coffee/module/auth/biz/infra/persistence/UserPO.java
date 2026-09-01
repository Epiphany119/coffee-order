package com.coffee.module.auth.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户持久化对象（映射 coffee_user 表）
 */
@TableName("coffee_user")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private LocalDate birthday;
    private String wechatId;
    private String qqNumber;
    private String email;
    private String otherInfo;
    private Double totalSpent;
    private String role;
    private Long lastStoreId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
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
