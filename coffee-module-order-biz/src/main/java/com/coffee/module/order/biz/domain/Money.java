package com.coffee.module.order.biz.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 金额值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Money {
    private BigDecimal originalAmount;
    private BigDecimal finalAmount;
    private BigDecimal discount;

    public static Money of(double amount) {
        return Money.builder()
                .originalAmount(BigDecimal.valueOf(amount))
                .finalAmount(BigDecimal.valueOf(amount))
                .discount(BigDecimal.ZERO)
                .build();
    }

    public static Money withDiscount(double original, double discount) {
        return Money.builder()
                .originalAmount(BigDecimal.valueOf(original))
                .finalAmount(BigDecimal.valueOf(original - discount))
                .discount(BigDecimal.valueOf(discount))
                .build();
    }

    public double getOriginalAmount() { return originalAmount.doubleValue(); }
    public double getFinalAmount() { return finalAmount.doubleValue(); }
    public double getDiscount() { return discount.doubleValue(); }
}
