package com.coffee.module.payment.biz.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单聚合根
 */
@Data
public class Payment {

    private Long id;

    /** 支付单号（PAY + yyyyMMddHHmmss + 4 位随机，全局唯一） */
    private String paymentNo;

    private Long orderId;

    /** 下单用户 id（游客单为 null） */
    private Long userId;

    /** 支付渠道 WECHAT/ALIPAY/BANK/MOCK */
    private String channel;

    /** 支付金额（= 订单实付 finalPrice） */
    private Double amount;

    private PaymentStatus status;

    /** 渠道交易流水号 */
    private String transactionNo;

    /** 渠道原始响应（调试/对账用） */
    private String channelResponse;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING("待支付"),
        PAID("已支付"),
        FAILED("支付失败"),
        CLOSED("已关闭"),
        REFUNDED("已退款");

        private final String description;
        PaymentStatus(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public enum Channel {
        WECHAT("微信支付"),
        ALIPAY("支付宝"),
        BANK("银行卡支付"),
        MOCK("模拟支付");

        private final String description;
        Channel(String description) { this.description = description; }
        public String getDescription() { return description; }
    }
}
