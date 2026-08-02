package com.coffee.order.strategy;

public class VipPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(double originalPrice) {
        return Math.round(originalPrice * 0.85 * 100.0) / 100.0;
    }

    @Override
    public String getStrategyName() {
        return "VIP会员 (85折)";
    }
}
