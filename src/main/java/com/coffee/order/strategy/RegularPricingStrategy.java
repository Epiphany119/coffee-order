package com.coffee.order.strategy;

public class RegularPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(double originalPrice) {
        return Math.round(originalPrice * 100.0) / 100.0;
    }

    @Override
    public String getStrategyName() {
        return "普通会员 (无折扣)";
    }
}
