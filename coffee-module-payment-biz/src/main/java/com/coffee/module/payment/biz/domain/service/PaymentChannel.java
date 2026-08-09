package com.coffee.module.payment.biz.domain.service;

import com.coffee.module.payment.biz.domain.Payment;

/**
 * 支付渠道策略接口：每接入一个真实渠道（微信/支付宝/银行）实现一个 Bean
 */
public interface PaymentChannel {

    /** 渠道编码：WECHAT / ALIPAY / BANK / MOCK */
    String channelCode();

    /** 发起支付：成功返回渠道交易流水号；未接入/失败抛 ServiceException */
    String pay(Payment payment);

    /** 处理渠道异步回调：验签校验后返回渠道交易流水号；校验失败抛 ServiceException */
    String handleCallback(Payment payment, String transactionNo);
}
