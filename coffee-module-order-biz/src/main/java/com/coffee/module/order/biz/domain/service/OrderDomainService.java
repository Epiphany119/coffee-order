package com.coffee.module.order.biz.domain.service;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 订单领域服务
 */
@Service
public class OrderDomainService {

    public static final int DEFAULT_PREPARE_MINUTES = 12;

    public Order createOrder(Long userId, String guestId, String beverageName, String size,
                            String condiments, Double originalPrice, Double finalPrice,
                            String categoryCode) {
        Order order = new Order();
        order.setUserId(userId);
        order.setGuestId(guestId);
        order.setBeverageName(beverageName);
        order.setSize(size);
        order.setCondiments(condiments);
        order.setOriginalPrice(originalPrice);
        order.setFinalPrice(finalPrice);
        order.setCategoryCode(categoryCode);
        order.setStatus(Order.OrderStatus.PENDING);
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
        order.setStatus(Order.OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setEstimatedReadyTime(order.getCreatedAt().plusMinutes(DEFAULT_PREPARE_MINUTES));
        return order;
    }

    public Order.OrderStatus calculateNextStatus(String currentStatus, String action) {
        return switch (currentStatus) {
            case "PENDING" -> action.equals("start") ? Order.OrderStatus.PREPARING : Order.OrderStatus.PENDING;
            case "PREPARING" -> action.equals("complete") ? Order.OrderStatus.COMPLETED :
                               action.equals("cancel") ? Order.OrderStatus.CANCELED : Order.OrderStatus.PREPARING;
            default -> Order.OrderStatus.valueOf(currentStatus);
        };
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
