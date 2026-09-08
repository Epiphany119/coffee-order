package com.coffee.module.delivery.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 顾客和配送员工作台共用的配送单响应；骑手视图会脱敏隐私字段。 */
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
    /** 商品快照；骑手、商家和顾客配送单均可查看，不包含顾客隐私字段。 */
    private List<DeliveryOrderItem> items;
    private String note;
    private String addressLabel;
    private String receiverName;
    /** 仅顾客自己的配送单返回；骑手接口固定为 null。 */
    private String receiverPhone;
    private String detailAddress;
    /** WAITING_MERCHANT / OPEN / CLAIMED / PICKED_UP / DELIVERING / DELIVERED / CANCELED */
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
