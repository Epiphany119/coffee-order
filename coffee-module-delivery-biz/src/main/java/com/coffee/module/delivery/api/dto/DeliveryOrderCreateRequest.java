package com.coffee.module.delivery.api.dto;

import lombok.Data;

/** 由订单模块在创建主订单后生成配送单的内部请求。 */
@Data
public class DeliveryOrderCreateRequest {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private String storeName;
    private Double amount;
    private String itemSummary;
    private Long addressId;
    private String note;
}
