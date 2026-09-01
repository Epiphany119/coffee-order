package com.coffee.module.delivery.api.dto;

import lombok.Data;

import java.time.LocalDate;

/** 配送员登录/注册响应。 */
@Data
public class DeliveryRiderResponse {
    private boolean success;
    private String message;
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatarUrl;
    private LocalDate birthday;
    private String email;
    private String otherInfo;
    private String status;
    private String accessToken;

    public static DeliveryRiderResponse ok(Long id, String username, String nickname,
                                           String phone, String status) {
        DeliveryRiderResponse response = new DeliveryRiderResponse();
        response.success = true;
        response.message = "操作成功";
        response.id = id;
        response.username = username;
        response.nickname = nickname;
        response.phone = phone;
        response.status = status;
        return response;
    }

    public static DeliveryRiderResponse fail(String message) {
        DeliveryRiderResponse response = new DeliveryRiderResponse();
        response.success = false;
        response.message = message;
        return response;
    }
}
