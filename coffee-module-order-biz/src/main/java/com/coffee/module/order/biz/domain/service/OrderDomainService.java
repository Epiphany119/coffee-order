package com.coffee.module.order.biz.domain.service;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 订单领域服务
 */
@Service
public class OrderDomainService {

    public static final int DEFAULT_PREPARE_MINUTES = 12;

    public Order createOrder(Long userId, String guestId, String beverageName, String size,
                            String customSize, String condiments, Double originalPrice, Double finalPrice,
                            String categoryCode) {
        Order order = new Order();
        order.setUserId(userId);
        order.setGuestId(guestId);
        order.setBeverageName(beverageName);
        order.setSize(size);
        order.setCustomSize(customSize);
        order.setCondiments(condiments);
        order.setOriginalPrice(originalPrice);
        order.setFinalPrice(finalPrice);
        order.setCategoryCode(categoryCode);
        order.setStatus(Order.OrderStatus.UNPAID);
        order.setCreatedAt(LocalDateTime.now());
        order.setEstimatedReadyTime(order.getCreatedAt().plusMinutes(DEFAULT_PREPARE_MINUTES));
        return order;
    }

    public Order createBatchOrder(Long userId, String guestId, String beverageName,
                                 Double originalPrice, Double finalPrice, int totalCups) {
        Order order = new Order();
        order.setUserId(userId);
        order.setGuestId(guestId);
        order.setBeverageName(beverageName);
        order.setOriginalPrice(originalPrice);
        order.setFinalPrice(finalPrice);
        order.setTotalCups(totalCups);
        order.setStatus(Order.OrderStatus.UNPAID);
        order.setCreatedAt(LocalDateTime.now());
        order.setEstimatedReadyTime(order.getCreatedAt().plusMinutes(DEFAULT_PREPARE_MINUTES));
        return order;
    }

    public Order.OrderStatus calculateNextStatus(String currentStatus, String action) {
        if (currentStatus == null || action == null || action.isBlank()) {
            throw new IllegalArgumentException("订单状态或操作不能为空");
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        return switch (currentStatus) {
            // 已支付订单不能直接取消，必须接入退款流程后再改变状态。
            case "UNPAID" -> "cancel".equals(normalized)
                    ? Order.OrderStatus.CANCELED
                    : invalidTransition(currentStatus, action);
            case "PENDING" -> "start".equals(normalized)
                    ? Order.OrderStatus.PREPARING
                    : invalidTransition(currentStatus, action);
            case "PREPARING" -> "complete".equals(normalized)
                    ? Order.OrderStatus.COMPLETED
                    : invalidTransition(currentStatus, action);
            case "COMPLETED", "CANCELED" -> invalidTransition(currentStatus, action);
            default -> throw new IllegalArgumentException("未知订单状态: " + currentStatus);
        };
    }

    private Order.OrderStatus invalidTransition(String currentStatus, String action) {
        throw new IllegalArgumentException("订单状态 " + currentStatus + " 不允许执行 " + action);
    }

    public int calculateEarnedPoints(double paidAmount) {
        return Math.max(0, (int) Math.floor(paidAmount));
    }

    public String formatReadyTime(LocalDateTime time) {
        return time.toLocalDate() + " " + time.toLocalTime().withSecond(0).withNano(0);
    }

    public LocalDateTime calculateReadyTime() {
        return LocalDateTime.now().plusMinutes(DEFAULT_PREPARE_MINUTES);
    }
}
