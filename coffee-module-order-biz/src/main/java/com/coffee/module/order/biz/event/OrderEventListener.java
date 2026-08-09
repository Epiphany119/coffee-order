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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化订单领域事件", e);
        }
        log.info("[Outbox] 已记录 {} 事件，订单 {}", event.getEventType(), event.getOrderId());
    }
}
