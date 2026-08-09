package com.coffee.web.controller;

import com.coffee.common.core.result.Result;
import com.coffee.module.payment.api.PaymentService;
import com.coffee.module.payment.api.dto.PaymentResponse;
import com.coffee.module.order.api.OrderQueryService;
import com.coffee.module.order.api.dto.OrderBrief;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.util.Map;

/**
 * 支付控制器
 */
@RestController
@RequestMapping("/api/pay")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderQueryService orderQueryService;
    private final String callbackSecret;

    public PaymentController(PaymentService paymentService, OrderQueryService orderQueryService,
                             @Value("${coffee.payment.callback-secret:change-this-callback-secret-before-production-2026}") String callbackSecret) {
        this.paymentService = paymentService;
        this.orderQueryService = orderQueryService;
        this.callbackSecret = callbackSecret;
    }

    /** 为订单创建支付单（幂等：同订单已有支付单直接返回） */
    @PostMapping("/create")
    public Result<PaymentResponse> create(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        requireOrderOwner(orderId);
        return Result.success(paymentService.createForOrder(orderId));
    }

    /** 发起支付：body { paymentNo, channel }，渠道 MOCK 直接成功，真实渠道骨架返回 501 未接入 */
    @PostMapping("/pay")
    public Result<PaymentResponse> pay(@RequestBody Map<String, Object> body) {
        String paymentNo = String.valueOf(body.get("paymentNo"));
        String channel = String.valueOf(body.get("channel"));
        requirePaymentOwner(paymentNo);
        return Result.success(paymentService.pay(paymentNo, channel));
    }

    /** 按支付单号查询 */
    @GetMapping("/{paymentNo}")
    public Result<PaymentResponse> getByPaymentNo(@PathVariable String paymentNo) {
        requirePaymentOwner(paymentNo);
        return Result.success(paymentService.getByPaymentNo(paymentNo));
    }

    /** 按订单查询支付单（前端"去支付"入口用） */
    @GetMapping("/order/{orderId}")
    public Result<PaymentResponse> getByOrderId(@PathVariable Long orderId) {
        requireOrderOwner(orderId);
        return Result.success(paymentService.getByOrderId(orderId));
    }

    /** 渠道异步回调入口（模拟/未来真实渠道：微信/支付宝/银行回调同一入口，验签在渠道内实现） */
    @PostMapping("/callback/{channel}")
    public Result<PaymentResponse> callback(@PathVariable String channel,
                                            @RequestHeader(value = "X-Payment-Callback-Secret", required = false) String signature,
                                            @RequestBody Map<String, Object> body) {
        if (signature == null || !MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                callbackSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new com.coffee.common.core.exception.ServiceException(401, "支付回调验签失败");
        }
        String paymentNo = String.valueOf(body.get("paymentNo"));
        String transactionNo = body.get("transactionNo") != null ? String.valueOf(body.get("transactionNo")) : null;
        return Result.success(paymentService.handleCallback(channel, paymentNo, transactionNo));
    }

    private void requirePaymentOwner(String paymentNo) {
        PaymentResponse payment = paymentService.getByPaymentNo(paymentNo);
        requireOrderOwner(payment.getOrderId());
    }

    private void requireOrderOwner(Long orderId) {
        OrderBrief order = orderQueryService.getOrderBrief(orderId);
        if (order == null) throw new com.coffee.common.core.exception.ServiceException(404, "订单不存在");
        if (order.getUserId() != null) AccessGuard.requireUser(order.getUserId());
        else AccessGuard.requireGuest(order.getGuestId());
    }
}
