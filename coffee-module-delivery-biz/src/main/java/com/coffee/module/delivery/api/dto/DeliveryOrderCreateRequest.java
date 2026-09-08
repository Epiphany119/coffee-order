package com.coffee.module.delivery.api.dto;

import lombok.Data;

import java.util.List;

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
    /** 下单时的商品快照，配送员和商家可以据此展示商品缩略图。 */
    private List<DeliveryOrderItem> items;
    private Long addressId;
    private String note;
}
