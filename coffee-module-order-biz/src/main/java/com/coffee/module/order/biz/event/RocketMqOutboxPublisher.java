package com.coffee.module.order.biz.event;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** rocketmq profile 下的真实 Producer；发送成功后 Outbox 才会标记为 PUBLISHED。 */
@Component
@Profile("rocketmq")
public class RocketMqOutboxPublisher implements OutboxPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqOutboxPublisher(RocketMQTemplate rocketMQTemplate,
                                   @Value("${coffee.rocketmq.order-topic:fika-order-events}") String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(String eventId, String eventType, String payload) {
        rocketMQTemplate.convertAndSend(topic + ":" + eventType, payload);
    }
}
