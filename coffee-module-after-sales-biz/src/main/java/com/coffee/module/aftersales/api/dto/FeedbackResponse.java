package com.coffee.module.aftersales.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单反馈响应
 */
@Data
public class FeedbackResponse {

    private Long id;

    /** 关联订单 id（取餐号） */
    private Long orderId;

    /** 反馈归属商品 id（订单第一个明细的商品） */
    private Long productId;

    /** 订单详细订单号 */
    private String orderNo;

    /** 订单商品名快照 */
    private String orderName;

    /** 反馈用户名（来自 coffee_user.username，唯一） */
    private String username;

    /** 建议内容 */
    private String content;

    /** 评分 1-5 */
    private Integer rating;

    private LocalDateTime createdAt;
}
