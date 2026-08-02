package com.coffee.order.strategy;

/**
 * 策略模式：定价策略接口
 * 不同客户类型使用不同的价格计算策略
 */
public interface PricingStrategy {
    double calculatePrice(double originalPrice);
    String getStrategyName();
}
