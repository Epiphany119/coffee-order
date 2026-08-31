package com.coffee.module.order.biz.event;

import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单事件落入 Outbox。监听器同步执行并加入订单的同一个本地事务：订单回滚时事件也回滚，
 * 订单提交成功后即使进程宕机，调度器仍可从 event_outbox 补偿投递。
 */
@Slf4j
@Component
public class OrderEventListener {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventListener(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void handleOrderEvent(OrderDomainEvent event) {
        try {
            jdbcTemplate.update("INSERT INTO event_outbox (event_id, aggregate_type, aggregate_id, event_type, payload, status, attempts, created_at, updated_at) "
                            + "VALUES (?, 'ORDER', ?, ?, ?, 'PENDING', 0, ?, ?)",
                    event.getEventId(), event.getOrderId(), event.getEventType(),
                    objectMapper.writeValueAsString(event), LocalDateTime.now(), LocalDateTime.now());
            if ("ORDER_STATUS_CHANGED".equals(event.getEventType()) && "DELIVERED".equals(event.getStatus())) {
                // 送达通知与主订单状态处在同一个本地事务中；重复事件不会重复通知。
                jdbcTemplate.update("INSERT INTO user_notification (user_id, type, title, content, read_status, created_at) "
                                + "SELECT o.user_id, 'DELIVERY_DELIVERED', ?, ?, 0, NOW() FROM user_order o "
                                + "WHERE o.id = ? AND o.user_id IS NOT NULL AND o.fulfillment_type = 'DELIVERY' "
                                + "AND o.status = 'DELIVERED' AND NOT EXISTS ("
                                + "SELECT 1 FROM user_notification n WHERE n.user_id = o.user_id "
                                + "AND n.type = 'DELIVERY_DELIVERED' AND n.title = ?)",
                        "骑手已送达 · #" + event.getOrderId(), "骑手已将订单送达，请前往取餐。",
                        event.getOrderId(), "骑手已送达 · #" + event.getOrderId());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化订单领域事件", e);
        }
        log.info("[Outbox] 已记录 {} 事件，订单 {}", event.getEventType(), event.getOrderId());
    }
}
