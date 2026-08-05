package com.coffee.order.domain.order.entity;

import java.time.LocalDateTime;

/**
 * 订单状态枚举
 */
public enum OrderStatus {
    PENDING("待处理"),
    PREPARING("制作中"),
    COMPLETED("已完成"),
    CANCELED("已取消");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
