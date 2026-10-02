package com.coffee.module.payment.biz.application.service;

import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Payment state persistence. REQUIRED propagation joins the surrounding payment
 * transaction so payment and order state transitions commit or roll back together.
 */
@Service
public class PaymentStateService {

    private final PaymentRepository paymentRepository;

    public PaymentStateService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean tryStartProcessing(Long paymentId) {
        return paymentRepository.tryStartProcessing(paymentId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean markPaidIfProcessing(Long paymentId, String channel, String transactionNo,
                                        LocalDateTime paidAt) {
        return paymentRepository.markPaidIfProcessing(paymentId, channel, transactionNo, paidAt);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean markPaidIfPendingOrProcessing(Long paymentId, String channel, String transactionNo,
                                                 LocalDateTime paidAt) {
        return paymentRepository.markPaidIfPendingOrProcessing(paymentId, channel, transactionNo, paidAt);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean resetProcessing(Long paymentId) {
        return paymentRepository.resetProcessing(paymentId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean markRefundedIfPaid(Long paymentId) {
        return paymentRepository.markRefundedIfPaid(paymentId);
    }
}
