package com.coffee.module.order.biz.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 默认实现：便于未部署 MQ 的本地环境验证 Outbox 流程。 */
@Slf4j
@Component
@Profile("!rocketmq")
public class LocalOutboxPublisher implements OutboxPublisher {
    @Override
    public void publish(String eventId, String eventType, String payload) {
        log.info("[Outbox][LOCAL] eventId={}, type={}, payload={}", eventId, eventType, payload);
    }
}
