package com.coffee.module.payment.biz.domain.service.channel;

import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道：直接支付成功，用于开发/演示跑通全流程
 */
@Component
public class MockPaymentChannel implements PaymentChannel {

    @Override
    public String channelCode() {
        return "MOCK";
    }

    @Override
    public String pay(Payment payment) {
        // 模拟支付：直接成功，返回模拟交易流水号
        return "MOCK" + System.currentTimeMillis();
    }

    @Override
    public String handleCallback(Payment payment, String transactionNo) {
        // 模拟渠道回调：有流水号直接用，没有则补一个
        return (transactionNo != null && !transactionNo.isBlank())
                ? transactionNo
                : "MOCK" + System.currentTimeMillis();
    }
}
