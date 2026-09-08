package com.coffee.module.delivery.api.dto;

import lombok.Data;

/** 配送单中的商品快照；图片和商品名取自下单时的订单明细，避免菜单变更影响履约查看。 */
@Data
public class DeliveryOrderItem {
    private String productCode;
    private String beverageName;
    private String imageUrl;
    private String size;
    private String condiments;
    private Integer quantity;
    private Double unitPrice;
    private Double originalUnitPrice;
    private Double subtotal;
}
