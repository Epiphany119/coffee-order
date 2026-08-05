package com.coffee.module.order.biz.event;

import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 订单事件监听
 */
@Slf4j
@Component
public class OrderEventListener {

    @Async
    @EventListener
    public void handleOrderEvent(OrderDomainEvent event) {
        switch (event.getEventType()) {
            case "ORDER_CREATED" -> log.info("[订单] 已创建: {} - {}", event.getOrderId(), event.getBeverageName());
            case "ORDER_STATUS_CHANGED" -> log.info("[订单] 状态变更: {} -> {}", event.getOrderId(), event.getStatus());
        }
    }
}
