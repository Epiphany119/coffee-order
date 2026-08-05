package com.coffee.module.order.api.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单响应
 */
@Data
public class OrderResponse {
    private Long orderId;
    private String orderName;
    private Double originalPrice;
    private Double finalPrice;
    private String pricingStrategy;
    private String status;
    private String message;
    private Double totalSpent;
    private String memberLevel;
    private String categoryCode;
    private Integer totalCups;
    private List<OrderItemResponse> items;
    private Double memberDiscount;
    private Double couponDiscount;
    private String couponName;
    private Integer earnedPoints;
    private String estimatedReadyTime;
    private LocalDateTime createdAt;

    @Data
    public static class OrderItemResponse {
        private String productCode;
        private String beverageName;
        private String categoryCode;
        private String size;
        private String condiments;
        private Integer quantity;
        private Double unitPrice;
        private Double subtotal;
        private String estimatedReadyTime;
    }
}
