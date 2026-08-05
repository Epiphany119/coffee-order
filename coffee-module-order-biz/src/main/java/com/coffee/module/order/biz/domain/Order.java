package com.coffee.module.order.biz.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单聚合根
 */
@Data
public class Order {
    private Long id;
    private Long userId;
    private String guestId;
    private String beverageName;
    private String size;
    private String condiments;
    private Double originalPrice;
    private Double finalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedReadyTime;
    private String strategyName;
    private String memberLevel;
    private Integer earnedPoints;
    private Double memberDiscount;
    private String couponName;
    private Double couponDiscount;
    private Integer totalCups;
    private String categoryCode;

    public enum OrderStatus {
        PENDING("待处理"),
        PREPARING("制作中"),
        COMPLETED("已完成"),
        CANCELED("已取消");

        private final String description;
        OrderStatus(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public boolean isUserOrder() {
        return userId != null && userId > 0;
    }

    public void transitionTo(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void addSpending(double amount) {
        if (this.originalPrice == null) this.originalPrice = 0.0;
        this.originalPrice += amount;
    }
}
