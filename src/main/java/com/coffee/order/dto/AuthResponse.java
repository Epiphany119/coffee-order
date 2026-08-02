package com.coffee.order.dto;

public class AuthResponse {
    private boolean success;
    private String message;
    private Long id;
    private String username;
    private String nickname;
    private double totalSpent;
    private String memberLevel;

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AuthResponse ok(Long userId, String username, String nickname, double totalSpent) {
        AuthResponse r = new AuthResponse(true, "操作成功");
        r.id = userId;
        r.username = username;
        r.nickname = nickname;
        r.totalSpent = totalSpent;
        r.memberLevel = calcLevel(totalSpent);
        return r;
    }

    private static String calcLevel(double totalSpent) {
        if (totalSpent >= 500) return "SVIP (7折)";
        if (totalSpent >= 300) return "VIP (85折)";
        return "普通会员";
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public double getTotalSpent() { return totalSpent; }
    public String getMemberLevel() { return memberLevel; }
}
