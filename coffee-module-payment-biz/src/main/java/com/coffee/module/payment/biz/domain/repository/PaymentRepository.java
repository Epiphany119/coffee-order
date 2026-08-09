package com.coffee.module.payment.biz.domain.repository;

import com.coffee.module.payment.biz.domain.Payment;

/**
 * 支付单仓储接口
 */
public interface PaymentRepository {

    /** 新增（id 为空）或更新（id 非空）支付单 */
    Payment save(Payment payment);

    Payment findById(Long id);

    Payment findByPaymentNo(String paymentNo);

    /** 按订单查最近一条支付单（无则 null） */
    Payment findByOrderId(Long orderId);
}
