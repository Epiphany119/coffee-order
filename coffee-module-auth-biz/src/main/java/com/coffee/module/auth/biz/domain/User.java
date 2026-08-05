package com.coffee.module.auth.biz.domain;

import java.time.LocalDateTime;

/**
 * 用户领域对象
 */
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Double totalSpent;
    private String role;
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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
