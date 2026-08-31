package com.coffee.module.payment.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付单持久化对象
 */
@Data
@TableName("payment")
public class PaymentPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付单号（PAY + 时间戳 + 4 位随机，唯一） */
    private String paymentNo;

    private Long orderId;

    /** 下单用户 id（游客单为 null） */
    private Long userId;

    /** 支付渠道 WECHAT/ALIPAY/BANK/MOCK */
    private String channel;

    private Double amount;

    /** 状态 PENDING/PROCESSING/PAID/FAILED/CLOSED/REFUNDED */
    private String status;

    /** 渠道交易流水号 */
    private String transactionNo;

    /** 渠道原始响应 */
    private String channelResponse;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
