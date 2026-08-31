package com.coffee.module.delivery.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 顾客和配送员工作台共用的配送单响应。 */
@Data
public class DeliveryOrderResponse {
    private Long id;
    private Long deliveryOrderId;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private String storeName;
    private Double amount;
    private String itemSummary;
    private String note;
    private String addressLabel;
    private String receiverName;
    private String receiverPhone;
    private String detailAddress;
    /** OPEN / CLAIMED / PICKED_UP / DELIVERING / DELIVERED / CANCELED */
    private String status;
    private String statusLabel;
    private Long riderId;
    private String riderName;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime updatedAt;
}
