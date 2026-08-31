package com.coffee.module.payment.biz.infra.repository;

import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import com.coffee.module.payment.biz.infra.persistence.PaymentMapper;
import com.coffee.module.payment.biz.infra.persistence.PaymentPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 支付单仓储实现
 */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentMapper paymentMapper;

    public PaymentRepositoryImpl(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentPO po = toPO(payment);
        if (payment.getId() == null) {
            paymentMapper.insert(po);
            payment.setId(po.getId());
        } else {
            paymentMapper.updateById(po);
        }
        return payment;
    }

    @Override
    public Payment findById(Long id) {
        PaymentPO po = paymentMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public Payment findByPaymentNo(String paymentNo) {
        PaymentPO po = paymentMapper.selectByPaymentNo(paymentNo);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public Payment findByOrderId(Long orderId) {
        PaymentPO po = paymentMapper.selectByOrderId(orderId);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public boolean tryStartProcessing(Long paymentId) {
        return paymentMapper.tryStartProcessing(paymentId) > 0;
    }

    @Override
    public boolean markPaidIfProcessing(Long paymentId, String channel, String transactionNo, LocalDateTime paidAt) {
        return paymentMapper.markPaidIfProcessing(paymentId, channel, transactionNo, paidAt) > 0;
    }

    @Override
    public boolean markPaidIfPendingOrProcessing(Long paymentId, String channel, String transactionNo,
                                                  LocalDateTime paidAt) {
        return paymentMapper.markPaidIfPendingOrProcessing(paymentId, channel, transactionNo, paidAt) > 0;
    }

    @Override
    public boolean resetProcessing(Long paymentId) {
        return paymentMapper.resetProcessing(paymentId) > 0;
    }

    @Override
    public boolean markRefundedIfPaid(Long paymentId) {
        return paymentMapper.markRefundedIfPaid(paymentId) > 0;
    }

    private Payment toDomain(PaymentPO po) {
        Payment payment = new Payment();
        payment.setId(po.getId());
        payment.setPaymentNo(po.getPaymentNo());
        payment.setOrderId(po.getOrderId());
        payment.setUserId(po.getUserId());
        payment.setChannel(po.getChannel());
        payment.setAmount(po.getAmount());
        payment.setStatus(Payment.PaymentStatus.valueOf(po.getStatus()));
        payment.setTransactionNo(po.getTransactionNo());
        payment.setChannelResponse(po.getChannelResponse());
        payment.setPaidAt(po.getPaidAt());
        payment.setCreatedAt(po.getCreatedAt());
        payment.setUpdatedAt(po.getUpdatedAt());
        return payment;
    }

    private PaymentPO toPO(Payment payment) {
        PaymentPO po = new PaymentPO();
        po.setId(payment.getId());
        po.setPaymentNo(payment.getPaymentNo());
        po.setOrderId(payment.getOrderId());
        po.setUserId(payment.getUserId());
        po.setChannel(payment.getChannel());
        po.setAmount(payment.getAmount());
        po.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        po.setTransactionNo(payment.getTransactionNo());
        po.setChannelResponse(payment.getChannelResponse());
        po.setPaidAt(payment.getPaidAt());
        po.setCreatedAt(payment.getCreatedAt());
        po.setUpdatedAt(payment.getUpdatedAt());
        return po;
    }
}
