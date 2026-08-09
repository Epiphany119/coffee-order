package com.coffee.module.aftersales.api.dto;

import lombok.Data;

/**
 * 提交订单反馈请求
 */
@Data
public class FeedbackRequest {

    /** 反馈用户 id */
    private Long userId;

    /** 关联订单 id */
    private Long orderId;

    /** 建议内容 */
    private String content;

    /** 评分 1-5（可选） */
    private Integer rating;
}
