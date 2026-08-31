package com.coffee.module.order.biz.event;

import com.coffee.module.member.api.MemberService;
import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 完成订单的积分/消费异步消费者；event_consume_log 保证 RocketMQ 重投不重复入账。 */
@Slf4j
@Component
@Profile("rocketmq")
@RocketMQMessageListener(topic = "${coffee.rocketmq.order-topic:fika-order-events}",
        consumerGroup = "fika-membership-consumer")
public class RocketMqOrderEventConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OrderRepository orderRepository;
    private final MemberService memberService;
    private final MembershipService membershipService;

    public RocketMqOrderEventConsumer(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, OrderRepository orderRepository,
                                      MemberService memberService, MembershipService membershipService) {
        this.objectMapper = objectMapper; this.jdbcTemplate = jdbcTemplate; this.orderRepository = orderRepository;
        this.memberService = memberService; this.membershipService = membershipService;
    }

    @Override
    @Transactional
    public void onMessage(String payload) {
        try {
            OrderDomainEvent event = objectMapper.readValue(payload, OrderDomainEvent.class);
            if (!"ORDER_STATUS_CHANGED".equals(event.getEventType())
                    || !("COMPLETED".equals(event.getStatus()) || "DELIVERED".equals(event.getStatus()))) return;
            int claimed = jdbcTemplate.update("INSERT IGNORE INTO event_consume_log (event_id, consumer, created_at) VALUES (?, 'membership', NOW())", event.getEventId());
            if (claimed == 0) return;
            Order order = orderRepository.findById(event.getOrderId());
            if (order != null && order.getUserId() != null && order.getUserId() > 0) {
                memberService.addSpending(order.getUserId(), order.getFinalPrice());
                membershipService.addConsumptionPoints(order.getUserId(), order.getFinalPrice());
                if ("COMPLETED".equals(event.getStatus())) {
                    jdbcTemplate.update("INSERT INTO user_notification (user_id, type, title, content, read_status, created_at) VALUES (?, 'ORDER_COMPLETED', ?, ?, 0, NOW())",
                            order.getUserId(), "订单已完成 · #" + order.getId(), "本次消费已入账，积分已发放到你的会员账户。");
                }
                log.info("[RocketMQ] 订单 {} 的会员积分已异步入账", order.getId());
            }
        } catch (Exception e) {
            // 抛出异常让 RocketMQ 触发重试；事务会同时回滚幂等日志与会员入账。
            throw new IllegalStateException("消费订单会员事件失败", e);
        }
    }
}
