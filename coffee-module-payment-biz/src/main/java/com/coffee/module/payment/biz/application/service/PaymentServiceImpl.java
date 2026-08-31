package com.coffee.module.payment.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderPaymentService;
import com.coffee.module.order.api.OrderQueryService;
import com.coffee.module.order.api.dto.OrderBrief;
import com.coffee.module.payment.api.PaymentService;
import com.coffee.module.payment.api.dto.PaymentResponse;
import com.coffee.module.payment.biz.domain.Payment;
import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import com.coffee.module.payment.biz.domain.service.PaymentChannel;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付应用服务：幂等创建支付单 / 发起支付 / 查询 / 渠道回调
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter PAY_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    private final OrderQueryService orderQueryService;
    private final OrderPaymentService orderPaymentService;
    private final PaymentStateService paymentStateService;
    private final List<PaymentChannel> channels;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              OrderQueryService orderQueryService,
                              OrderPaymentService orderPaymentService,
                              PaymentStateService paymentStateService,
                              List<PaymentChannel> channels) {
        this.paymentRepository = paymentRepository;
        this.orderQueryService = orderQueryService;
        this.orderPaymentService = orderPaymentService;
        this.paymentStateService = paymentStateService;
        this.channels = channels;
    }

    @Override
    @Transactional
    public PaymentResponse createForOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ServiceException(400, "订单 id 无效");
        }
        // 幂等：同订单已有支付单（含已支付）直接返回，不重复创建
        Payment existing = paymentRepository.findByOrderId(orderId);
        if (existing != null) {
            return toResponse(existing);
        }
        OrderBrief brief = orderQueryService.getOrderBrief(orderId);
        if (brief == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (!"UNPAID".equals(brief.getStatus())) {
            throw new ServiceException(409, "订单当前状态不可创建支付单");
        }
        if (brief.getFinalPrice() == null || brief.getFinalPrice() < 0) {
            throw new ServiceException(409, "订单金额无效，无法创建支付单");
        }
        Payment payment = new Payment();
        payment.setPaymentNo(nextPaymentNo());
        payment.setOrderId(orderId);
        payment.setUserId(brief.getUserId());
        payment.setChannel(Payment.Channel.MOCK.name());
        payment.setAmount(brief.getFinalPrice());
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(payment.getCreatedAt());
        try {
            paymentRepository.save(payment);
        } catch (DuplicateKeyException duplicateKeyException) {
            // 配合 payment(order_id) 唯一索引处理多实例并发创建。
            Payment concurrent = paymentRepository.findByOrderId(orderId);
            if (concurrent != null) return toResponse(concurrent);
            throw duplicateKeyException;
        }
        return toResponse(payment);
    }

    @Override
    public PaymentResponse pay(String paymentNo, String channelCode) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new ServiceException(404, "支付单不存在");
        }
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new ServiceException(409, "支付单已支付或已关闭，请勿重复支付");
        }
        // 订单须仍处于待支付状态（防止订单已取消仍被支付）
        orderPaymentService.checkPayable(payment.getOrderId());

        if (!paymentStateService.tryStartProcessing(payment.getId())) {
            throw new ServiceException(409, "支付请求正在处理中，请稍后查询支付状态");
        }
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        boolean paidPersisted = false;
        try {
            PaymentChannel channel = resolveChannel(channelCode);
            String normalizedChannel = channelCode.trim().toUpperCase(Locale.ROOT);
            String transactionNo = requireTransactionNo(channel.pay(payment)); // Mock 直接成功；真实渠道骨架抛 501
            LocalDateTime paidAt = LocalDateTime.now();
            if (!paymentStateService.markPaidIfProcessing(payment.getId(), normalizedChannel, transactionNo, paidAt)) {
                throw new ServiceException(409, "支付状态已变化，请重新查询");
            }
            paidPersisted = true;

            payment.setChannel(normalizedChannel);
            payment.setTransactionNo(transactionNo);
            payment.setStatus(Payment.PaymentStatus.PAID);
            payment.setPaidAt(paidAt);
            payment.setUpdatedAt(paidAt);
            try {
                if (!orderPaymentService.markPaid(payment.getOrderId())) {
                    throw new ServiceException(409, "订单已取消，支付结果未进入订单，请核对支付状态");
                }
            } catch (RuntimeException exception) {
                // MOCK 没有真实资金，订单并发取消时可以安全标记为已冲正；真实渠道必须接入原渠道退款 API。
                if (Payment.Channel.MOCK.name().equals(normalizedChannel)) {
                    paymentStateService.markRefundedIfPaid(payment.getId());
                    payment.setStatus(Payment.PaymentStatus.REFUNDED);
                }
                throw exception;
            }
            return toResponse(payment);
        } catch (RuntimeException exception) {
            if (!paidPersisted && payment.getStatus() == Payment.PaymentStatus.PROCESSING) {
                paymentStateService.resetProcessing(payment.getId());
            }
            throw exception;
        }
    }

    @Override
    public PaymentResponse handleCallback(String channelCode, String paymentNo, String transactionNo) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new ServiceException(404, "支付单不存在");
        }
        if (payment.getStatus() == Payment.PaymentStatus.PAID) return toResponse(payment);
        if (payment.getStatus() != Payment.PaymentStatus.PENDING
                && payment.getStatus() != Payment.PaymentStatus.PROCESSING) {
            throw new ServiceException(409, "支付单当前状态不接受回调");
        }
        if (payment.getChannel() != null && !payment.getChannel().equalsIgnoreCase(channelCode)) {
            throw new ServiceException(400, "支付回调渠道与支付单不匹配");
        }
        // 回调落账前再次检查订单状态，防止已取消订单被异步回调改成已支付。
        orderPaymentService.checkPayable(payment.getOrderId());
        PaymentChannel channel = resolveChannel(channelCode);
        String normalizedChannel = channelCode.trim().toUpperCase(Locale.ROOT);
        String txNo = requireTransactionNo(channel.handleCallback(payment, transactionNo));
        LocalDateTime paidAt = LocalDateTime.now();
        if (!paymentStateService.markPaidIfPendingOrProcessing(payment.getId(), normalizedChannel, txNo, paidAt)) {
            Payment latest = paymentRepository.findByPaymentNo(paymentNo);
            if (latest != null && latest.getStatus() == Payment.PaymentStatus.PAID) return toResponse(latest);
            throw new ServiceException(409, "支付状态已变化，请重新查询");
        }
        payment.setChannel(normalizedChannel);
        payment.setTransactionNo(txNo);
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setUpdatedAt(paidAt);
        try {
            if (!orderPaymentService.markPaid(payment.getOrderId())) {
                throw new ServiceException(409, "订单已取消，支付结果未进入订单，请核对支付状态");
            }
        } catch (RuntimeException exception) {
            if (Payment.Channel.MOCK.name().equals(normalizedChannel)) {
                paymentStateService.markRefundedIfPaid(payment.getId());
                payment.setStatus(Payment.PaymentStatus.REFUNDED);
            }
            throw exception;
        }
        return toResponse(payment);
    }

    @Override
    public PaymentResponse getByPaymentNo(String paymentNo) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new ServiceException(404, "支付单不存在");
        }
        return toResponse(payment);
    }

    @Override
    public PaymentResponse getByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment == null) {
            throw new ServiceException(404, "该订单暂无支付单");
        }
        return toResponse(payment);
    }

    private PaymentChannel resolveChannel(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            throw new ServiceException(400, "请选择支付渠道");
        }
        for (PaymentChannel c : channels) {
            if (c.channelCode().equalsIgnoreCase(channelCode)) {
                return c;
            }
        }
        throw new ServiceException(400, "不支持的支付渠道: " + channelCode);
    }

    private String requireTransactionNo(String transactionNo) {
        if (transactionNo == null || transactionNo.isBlank()) {
            throw new ServiceException(502, "支付渠道未返回交易流水号");
        }
        String normalized = transactionNo.trim();
        if (normalized.length() > 100) {
            throw new ServiceException(502, "支付渠道交易流水号过长");
        }
        return normalized;
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAY_NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setPaymentNo(payment.getPaymentNo());
        response.setOrderId(payment.getOrderId());
        response.setUserId(payment.getUserId());
        response.setChannel(payment.getChannel());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus().name());
        response.setStatusDesc(payment.getStatus().getDescription());
        response.setTransactionNo(payment.getTransactionNo());
        response.setPaidAt(payment.getPaidAt());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
