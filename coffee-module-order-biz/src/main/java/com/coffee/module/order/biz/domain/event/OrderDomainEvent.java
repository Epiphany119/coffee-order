package com.coffee.module.order.biz.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 订单领域事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDomainEvent {
    private String eventId;
    private String eventType;
    private Long orderId;
    private String beverageName;
    private String status;
    private LocalDateTime occurredOn;
    private Object payload;

    public static OrderDomainEvent created(Long orderId, String beverageName, String status) {
        OrderDomainEvent event = new OrderDomainEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType("ORDER_CREATED");
        event.setOrderId(orderId);
        event.setBeverageName(beverageName);
        event.setStatus(status);
        event.setOccurredOn(LocalDateTime.now());
        return event;
    }

    public static OrderDomainEvent statusChanged(Long orderId, String beverageName, String newStatus) {
        OrderDomainEvent event = new OrderDomainEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType("ORDER_STATUS_CHANGED");
        event.setOrderId(orderId);
        event.setBeverageName(beverageName);
        event.setStatus(newStatus);
        event.setOccurredOn(LocalDateTime.now());
        return event;
    }
}
