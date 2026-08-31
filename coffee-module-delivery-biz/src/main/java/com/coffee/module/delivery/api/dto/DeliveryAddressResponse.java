package com.coffee.module.delivery.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 顾客收货地址响应。 */
@Data
public class DeliveryAddressResponse {
    private Long id;
    private String label;
    private String receiverName;
    private String receiverPhone;
    private String detailAddress;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
