package com.coffee.order.domain.order.valueobject;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单行项值对象 - 不可变
 */
public record OrderLineItem(
    String productCode,
    String beverageName,
    String categoryCode,
    String size,
    List<String> condiments,
    int quantity,
    double unitPrice,
    double subtotal,
    LocalDateTime estimatedReadyTime
) {
    public OrderLineItem {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("商品编码不能为空");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }
    }

    public static OrderLineItem create(
            String productCode,
            String beverageName,
            String categoryCode,
            String size,
            List<String> condiments,
            int quantity,
            double unitPrice,
            LocalDateTime estimatedReadyTime) {
        double subtotal = Math.round(unitPrice * quantity * 100.0) / 100.0;
        return new OrderLineItem(
                productCode, beverageName, categoryCode, size,
                List.copyOf(condiments != null ? condiments : List.of()),
                quantity, unitPrice, subtotal, estimatedReadyTime);
    }
}
