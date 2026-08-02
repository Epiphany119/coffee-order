package com.coffee.order.strategy;

public class SvipPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(double originalPrice) {
        return Math.round(originalPrice * 0.7 * 100.0) / 100.0;
    }

    @Override
    public String getStrategyName() {
        return "SVIP会员 (7折)";
    }
}
