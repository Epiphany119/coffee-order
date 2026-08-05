package com.coffee.module.member.biz.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会员领域对象
 */
@Data
public class Member {
    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Double totalSpent;
    private String memberLevel;
    private Integer points;
    private String pointsLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static final double VIP_THRESHOLD = 100;
    public static final double SVIP_THRESHOLD = 500;

    public String getLevel() {
        if (totalSpent != null && totalSpent >= SVIP_THRESHOLD) return "SVIP";
        if (totalSpent != null && totalSpent >= VIP_THRESHOLD) return "VIP";
        return "REGULAR";
    }

    public double getDiscountRate() {
        return switch (getLevel()) {
            case "SVIP" -> 0.90;
            case "VIP" -> 0.95;
            default -> 1.0;
        };
    }

    public void addSpending(double amount) {
        if (this.totalSpent == null) this.totalSpent = 0.0;
        this.totalSpent += amount;
        this.memberLevel = getLevel();
    }

    public void addPoints(int points) {
        if (this.points == null) this.points = 0;
        this.points += points;
        this.pointsLevel = getPointsLevel();
    }

    public String getPointsLevel() {
        if (points != null && points >= 1000) return "GOLD";
        if (points != null && points >= 500) return "SILVER";
        return "BRONZE";
    }
}
