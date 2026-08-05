package com.coffee.module.auth.api.dto;

import com.coffee.module.member.api.dto.MemberLevelDTO;

/**
 * 认证响应（注册/登录/用户信息通用）
 */
public class AuthResponse {
    private boolean success;
    private String message;
    private Long id;
    private String username;
    private String nickname;
    private Double totalSpent;
    private String memberLevel;

    public AuthResponse() {}

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AuthResponse ok(Long userId, String username, String nickname, double totalSpent) {
        AuthResponse r = new AuthResponse();
        r.success = true;
        r.message = "操作成功";
        r.id = userId;
        r.username = username;
        r.nickname = nickname;
        r.totalSpent = totalSpent;
        r.memberLevel = MemberLevelDTO.fromTotalSpent(totalSpent).getLabel();
        return r;
    }

    public static AuthResponse fail(String message) {
        return new AuthResponse(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }
    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
}
