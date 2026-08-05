package com.coffee.order.domain.member.valueobject;

import java.time.LocalDateTime;

/**
 * 会员等级值对象
 */
public record MemberLevel(
    String level,
    String label,
    double discountRate,
    String benefits
) {
    public static final MemberLevel REGULAR = new MemberLevel("REGULAR", "普通会员", 1.0, "无折扣");
    public static final MemberLevel VIP = new MemberLevel("VIP", "VIP会员", 0.95, "全场95折");
    public static final MemberLevel SVIP = new MemberLevel("SVIP", "SVIP会员", 0.90, "全场9折");

    public static final double VIP_THRESHOLD = 100;
    public static final double SVIP_THRESHOLD = 500;

    public static MemberLevel fromTotalSpent(double totalSpent) {
        if (totalSpent >= SVIP_THRESHOLD) return SVIP;
        if (totalSpent >= VIP_THRESHOLD) return VIP;
        return REGULAR;
    }

    public static String getLabel(String level) {
        return switch (level) {
            case "VIP" -> "VIP会员";
            case "SVIP" -> "SVIP会员";
            default -> "普通会员";
        };
    }
}
