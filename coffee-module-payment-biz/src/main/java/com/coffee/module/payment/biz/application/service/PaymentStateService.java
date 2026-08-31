package com.coffee.module.payment.biz.application.service;

import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付状态持久化边界。
 *
 * <p>支付渠道调用属于外部 I/O，不能和数据库事务绑在同一个长事务里。
 * 每个状态迁移单独提交，避免渠道已经扣款后业务异常回滚成一个永远卡在
 * PROCESSING 的支付单。</p>
 */
@Service
public class PaymentStateService {

    private final PaymentRepository paymentRepository;

    public PaymentStateService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryStartProcessing(Long paymentId) {
        return paymentRepository.tryStartProcessing(paymentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPaidIfProcessing(Long paymentId, String channel, String transactionNo,
                                        LocalDateTime paidAt) {
        return paymentRepository.markPaidIfProcessing(paymentId, channel, transactionNo, paidAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPaidIfPendingOrProcessing(Long paymentId, String channel, String transactionNo,
                                                 LocalDateTime paidAt) {
        return paymentRepository.markPaidIfPendingOrProcessing(paymentId, channel, transactionNo, paidAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean resetProcessing(Long paymentId) {
        return paymentRepository.resetProcessing(paymentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRefundedIfPaid(Long paymentId) {
        return paymentRepository.markRefundedIfPaid(paymentId);
    }
}
