package com.coffee.module.delivery.api.dto;

import lombok.Data;

/** 配送员登录请求。 */
@Data
public class DeliveryRiderLoginRequest {
    private String username;
    private String password;
}
