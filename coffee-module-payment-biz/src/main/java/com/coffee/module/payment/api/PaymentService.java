package com.coffee.module.payment.api;

import com.coffee.module.payment.api.dto.PaymentResponse;

/**
 * 支付服务接口（供 coffee-web 编排下单支付流程）
 */
public interface PaymentService {

    /** 为订单创建支付单（幂等：同订单已有支付单直接返回；订单不存在抛 404） */
    PaymentResponse createForOrder(Long orderId);

    /** 发起支付：渠道 MOCK 直接成功；微信/支付宝/银行骨架抛 501 未接入 */
    PaymentResponse pay(String paymentNo, String channel);

    /** 按支付单号查询 */
    PaymentResponse getByPaymentNo(String paymentNo);

    /** 按订单查询支付单（前端"去支付"入口拉取 paymentNo 用；无则 404） */
    PaymentResponse getByOrderId(Long orderId);

    /** 渠道异步回调入口（模拟/未来真实渠道回调）：校验后回写 PAID + 订单 UNPAID→PENDING */
    PaymentResponse handleCallback(String channel, String paymentNo, String transactionNo);
}
