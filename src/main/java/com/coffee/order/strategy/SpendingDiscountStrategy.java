package com.coffee.order.strategy;

/**
 * 策略模式扩展：根据累计消费自动确定折扣等级
 * 满300元 → VIP (85折)
 * 满500元 → SVIP (7折)
 */
public class SpendingDiscountStrategy {
    public static final double VIP_THRESHOLD = 300.0;
    public static final double SVIP_THRESHOLD = 500.0;

    public static String getLevel(double totalSpent) {
        if (totalSpent >= SVIP_THRESHOLD) return "svip";
        if (totalSpent >= VIP_THRESHOLD) return "vip";
        return "regular";
    }

    public static String getLevelLabel(double totalSpent) {
        if (totalSpent >= SVIP_THRESHOLD) return "SVIP (7折)";
        if (totalSpent >= VIP_THRESHOLD) return "VIP (85折)";
        return "普通会员";
    }
}
