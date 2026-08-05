package com.coffee.order.domain.order.valueobject;

/**
 * 金额值对象 - 包含原价和最终价
 */
public record Money(
    double originalAmount,
    double finalAmount,
    double discount
) {
    public static Money of(double amount) {
        return new Money(amount, amount, 0);
    }

    public static Money withDiscount(double original, double discount) {
        double finalAmount = Math.round((original - discount) * 100.0) / 100.0;
        return new Money(original, finalAmount, discount);
    }

    public double getOriginalAmount() {
        return originalAmount;
    }

    public double getFinalAmount() {
        return finalAmount;
    }

    public double getDiscount() {
        return discount;
    }
}
