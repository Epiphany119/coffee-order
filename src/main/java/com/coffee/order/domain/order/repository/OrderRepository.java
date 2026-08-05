package com.coffee.order.domain.order.repository;

import com.coffee.order.domain.order.aggregate.OrderAggregate;
import com.coffee.order.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储接口
 */
public interface OrderRepository {
    OrderAggregate save(OrderAggregate order);
    Optional<OrderAggregate> findById(Long id);
    List<OrderAggregate> findByUserId(Long userId);
    List<OrderAggregate> findByGuestId(String guestId);
    List<OrderAggregate> findByStatus(OrderStatus status);
    List<OrderAggregate> findAll();
    void updateStatus(Long orderId, OrderStatus status);
    List<OrderAggregate> findReadyBefore(LocalDateTime time);
}
