package com.coffee.module.delivery.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 配送员个人业绩看板数据。金额是已送达配送单的订单金额，不等同于骑手收入。 */
@Data
public class DeliveryRiderPerformanceResponse {
    /** 7d/14d/28d/12w。 */
    private String range;
    private String rangeLabel;
    /** DAY 或 WEEK，供前端决定横轴展示。 */
    private String bucket;
    private int rangeAssigned;
    private int rangeDelivered;
    private BigDecimal rangeAmount;
    private BigDecimal averageOrderAmount;
    private int todayAssigned;
    private int todayDelivered;
    private int activeOrders;
    private int weekDelivered;
    private int totalDelivered;
    private BigDecimal totalDeliveredAmount;
    private boolean deliveryFeeConfigured;
    private String deliveryFeeLabel;
    private List<DailyPoint> daily;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        private String day;
        private int delivered;
        private BigDecimal amount;
    }
}
