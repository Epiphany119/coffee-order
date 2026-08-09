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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private final List<PaymentChannel> channels;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              OrderQueryService orderQueryService,
                              OrderPaymentService orderPaymentService,
                              List<PaymentChannel> channels) {
        this.paymentRepository = paymentRepository;
        this.orderQueryService = orderQueryService;
        this.orderPaymentService = orderPaymentService;
        this.channels = channels;
    }

    @Override
    @Transactional
    public PaymentResponse createForOrder(Long orderId) {
        // 幂等：同订单已有支付单（含已支付）直接返回，不重复创建
        Payment existing = paymentRepository.findByOrderId(orderId);
        if (existing != null) {
            return toResponse(existing);
        }
        OrderBrief brief = orderQueryService.getOrderBrief(orderId);
        if (brief == null) {
            throw new ServiceException(404, "订单不存在");
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
        paymentRepository.save(payment);
        return toResponse(payment);
    }

    @Override
    @Transactional
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

        PaymentChannel channel = resolveChannel(channelCode);
        String transactionNo = channel.pay(payment); // Mock 直接成功；真实渠道骨架抛 501

        payment.setChannel(channelCode.toUpperCase());
        payment.setTransactionNo(transactionNo);
        payment.setStatus(Payment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(payment.getPaidAt());
        paymentRepository.save(payment);
        // 订单状态回写：UNPAID → PENDING（幂等）
        orderPaymentService.markPaid(payment.getOrderId());
        return toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse handleCallback(String channelCode, String paymentNo, String transactionNo) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new ServiceException(404, "支付单不存在");
        }
        PaymentChannel channel = resolveChannel(channelCode);
        String txNo = channel.handleCallback(payment, transactionNo); // 验签在渠道内实现；骨架抛 501

        // 回调幂等：仅 PENDING 状态回写；已 PAID 直接返回现状
        if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
            payment.setChannel(channelCode.toUpperCase());
            payment.setTransactionNo(txNo);
            payment.setStatus(Payment.PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payment.setUpdatedAt(payment.getPaidAt());
            paymentRepository.save(payment);
            // TODO 订单若已取消（CANCELED）需触发退款流程，暂未接入
            orderPaymentService.markPaid(payment.getOrderId());
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
