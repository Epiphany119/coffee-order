package com.coffee.order.application.event;

import com.coffee.order.domain.order.event.OrderDomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订单领域事件监听器
 */
@Component
public class OrderEventListener {

    @EventListener
    public void handleOrderEvent(OrderDomainEvent event) {
        switch (event.eventType()) {
            case "ORDER_CREATED" -> handleOrderCreated(event);
            case "ORDER_STATUS_CHANGED" -> handleStatusChanged(event);
            case "ORDER_READY" -> handleOrderReady(event);
        }
    }

    private void handleOrderCreated(OrderDomainEvent event) {
        System.out.println("[OrderEvent] 订单已创建: " + event.orderId() + " - " + event.beverageName());
    }

    private void handleStatusChanged(OrderDomainEvent event) {
        System.out.println("[OrderEvent] 订单状态变更: " + event.orderId() + " -> " + event.status());
    }

    private void handleOrderReady(OrderDomainEvent event) {
        System.out.println("[OrderEvent] 订单已完成: " + event.orderId() + " - " + event.beverageName());
    }
}
