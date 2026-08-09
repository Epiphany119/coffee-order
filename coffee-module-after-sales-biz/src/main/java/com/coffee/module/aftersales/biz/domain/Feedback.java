package com.coffee.module.aftersales.biz.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单反馈领域对象
 */
@Data
public class Feedback {

    private Long id;

    /** 关联订单 id（user_order.id） */
    private Long orderId;

    /** 反馈归属商品 id（订单第一个明细的商品，规则：反馈记录挂在第一个品下） */
    private Long productId;

    /** 反馈用户（coffee_user.id） */
    private Long userId;

    /** 建议内容 */
    private String content;

    /** 评分 1-5（可选） */
    private Integer rating;

    private LocalDateTime createdAt;
}
