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
    /** 详细订单号：YYMMDD-商家6位-类目3位-店铺当日顺序3位（如 260806-687257-007-001） */
    private String orderNo;
    private String orderName;
    private Double originalPrice;
    private Double finalPrice;
    private String pricingStrategy;
    private String status;
    /** 支付单号（下单时由支付模块创建，供前端拉起支付） */
    private String paymentNo;
    /** 外卖配送单 id；非外卖订单为空。 */
    private Long deliveryOrderId;
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
        /** 单件原价（折前，划线展示用） */
        private Double originalUnitPrice;
        private Double subtotal;
        private String estimatedReadyTime;
    }
}
