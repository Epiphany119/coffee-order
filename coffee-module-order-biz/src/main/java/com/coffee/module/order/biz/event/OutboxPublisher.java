package com.coffee.module.order.biz.event;

/** Outbox 发送适配层：本地开发日志与 RocketMQ 实现可无缝切换。 */
public interface OutboxPublisher {
    void publish(String eventId, String eventType, String payload);
}
