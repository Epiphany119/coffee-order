package com.coffee.order.domain.order.event;

import com.coffee.order.domain.order.entity.OrderStatus;
import java.time.LocalDateTime;

/**
 * 订单领域事件
 */
public record OrderDomainEvent(
    String eventId,
    String eventType,
    Long orderId,
    String beverageName,
    OrderStatus status,
    LocalDateTime occurredOn,
    Object payload
) {
    public static OrderDomainEvent orderCreated(Long orderId, String beverageName, OrderStatus status) {
        return new OrderDomainEvent(
            java.util.UUID.randomUUID().toString(),
            "ORDER_CREATED",
            orderId,
            beverageName,
            status,
            LocalDateTime.now(),
            null
        );
    }

    public static OrderDomainEvent orderStatusChanged(Long orderId, String beverageName, OrderStatus newStatus) {
        return new OrderDomainEvent(
            java.util.UUID.randomUUID().toString(),
            "ORDER_STATUS_CHANGED",
            orderId,
            beverageName,
            newStatus,
            LocalDateTime.now(),
            null
        );
    }

    public static OrderDomainEvent orderReady(Long orderId, String beverageName) {
        return new OrderDomainEvent(
            java.util.UUID.randomUUID().toString(),
            "ORDER_READY",
            orderId,
            beverageName,
            OrderStatus.COMPLETED,
            LocalDateTime.now(),
            null
        );
    }
}
