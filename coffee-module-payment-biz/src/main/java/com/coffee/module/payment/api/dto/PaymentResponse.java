package com.coffee.module.payment.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单响应
 */
@Data
public class PaymentResponse {

    private Long paymentId;

    /** 支付单号（PAY + 时间戳 + 4 位随机，唯一） */
    private String paymentNo;

    private Long orderId;

    /** 下单用户 id（游客单为 null） */
    private Long userId;

    /** 支付渠道 WECHAT/ALIPAY/BANK/MOCK */
    private String channel;

    /** 支付金额 */
    private Double amount;

    /** 状态：PENDING/PAID/FAILED/CLOSED/REFUNDED */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 渠道交易流水号（支付成功后生成） */
    private String transactionNo;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
