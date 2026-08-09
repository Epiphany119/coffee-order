package com.coffee.module.order.biz.event;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outbox 补偿投递器。当前默认本地日志 publisher，接入 RocketMQ 后只替换 publish 方法；
 * PENDING 行会在发送成功后置为 PUBLISHED，失败增加次数等待下一轮重试。
 */
@Slf4j
@Component
public class OutboxDispatcher {
    private final JdbcTemplate jdbcTemplate;
    private final OutboxPublisher outboxPublisher;
    private final MeterRegistry meterRegistry;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final AtomicInteger pendingEvents = new AtomicInteger();

    public OutboxDispatcher(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry, OutboxPublisher outboxPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxPublisher = outboxPublisher;
        this.meterRegistry = meterRegistry;
        this.publishedCounter = Counter.builder("fika.outbox.published")
                .description("Successfully published outbox events").register(meterRegistry);
        this.failedCounter = Counter.builder("fika.outbox.failed")
                .description("Failed outbox publish attempts").register(meterRegistry);
        meterRegistry.gauge("fika.outbox.pending", pendingEvents);
    }

    @Scheduled(fixedDelayString = "${coffee.outbox.dispatch-interval-ms:3000}")
    public void dispatchPendingEvents() {
        // 进程在发送过程中崩溃时，租约过期后恢复为待投递，避免永久卡死。
        jdbcTemplate.update("UPDATE event_outbox SET status = 'PENDING', updated_at = ? "
                        + "WHERE status = 'PUBLISHING' AND updated_at < DATE_SUB(?, INTERVAL 5 MINUTE)",
                LocalDateTime.now(), LocalDateTime.now());
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT id, event_id, event_type, payload FROM event_outbox WHERE status = 'PENDING' ORDER BY id LIMIT 100");
        pendingEvents.set(events.size());
        for (Map<String, Object> event : events) {
            Long id = ((Number) event.get("id")).longValue();
            // 条件更新相当于数据库 CAS：水平扩容后，只有一个实例有资格真正投递。
            int claimed = jdbcTemplate.update("UPDATE event_outbox SET status = 'PUBLISHING', updated_at = ? "
                    + "WHERE id = ? AND status = 'PENDING'", LocalDateTime.now(), id);
            if (claimed == 0) continue;
            try {
                publish(event);
                jdbcTemplate.update("UPDATE event_outbox SET status = 'PUBLISHED', published_at = ?, updated_at = ? "
                                + "WHERE id = ? AND status = 'PUBLISHING'", LocalDateTime.now(), LocalDateTime.now(), id);
                publishedCounter.increment();
                pendingEvents.decrementAndGet();
            } catch (Exception e) {
                jdbcTemplate.update("UPDATE event_outbox SET status = 'PENDING', attempts = attempts + 1, last_error = ?, updated_at = ? "
                                + "WHERE id = ? AND status = 'PUBLISHING'",
                        abbreviate(e.getMessage()), LocalDateTime.now(), id);
                failedCounter.increment();
                log.warn("[Outbox] 投递事件 {} 失败，将重试", event.get("event_id"), e);
            }
        }
    }

    private void publish(Map<String, Object> event) {
        outboxPublisher.publish(String.valueOf(event.get("event_id")),
                String.valueOf(event.get("event_type")), String.valueOf(event.get("payload")));
    }

    private String abbreviate(String message) {
        if (message == null) return "unknown error";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
