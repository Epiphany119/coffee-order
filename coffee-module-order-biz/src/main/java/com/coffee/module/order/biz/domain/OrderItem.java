package com.coffee.module.order.biz.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单行项值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    /** 商品 id（menu_item.id，落 order_item.product_id） */
    private Long productId;
    private String productCode;
    private String beverageName;
    /** 商品图片快照，避免菜单图片更新后历史订单图片跟着变化。 */
    private String imageUrl;
    private String categoryCode;
    private String size;
    private List<String> condiments;
    private Integer quantity;
    private Double unitPrice;
    /** 单件原价（折前，划线展示用） */
    private Double originalUnitPrice;
    private Double subtotal;
    private LocalDateTime estimatedReadyTime;

    public static OrderItem create(String productCode, String beverageName, String categoryCode,
                                   String size, List<String> condiments, Integer quantity,
                                   Double unitPrice, LocalDateTime estimatedReadyTime) {
        double subtotal = Math.round(unitPrice * quantity * 100.0) / 100.0;
        return OrderItem.builder()
                .productCode(productCode)
                .beverageName(beverageName)
                .categoryCode(categoryCode)
                .size(size)
                .condiments(condiments)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .estimatedReadyTime(estimatedReadyTime)
                .build();
    }
}
