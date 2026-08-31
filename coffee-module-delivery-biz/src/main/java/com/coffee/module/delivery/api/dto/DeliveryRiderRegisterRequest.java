package com.coffee.module.delivery.api.dto;

import lombok.Data;

/** 配送员注册请求。当前先采用平台自注册，后续可增加审核状态。 */
@Data
public class DeliveryRiderRegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String phone;
}
