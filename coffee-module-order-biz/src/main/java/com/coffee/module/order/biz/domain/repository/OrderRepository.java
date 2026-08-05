package com.coffee.module.order.biz.domain.repository;

import com.coffee.module.order.biz.domain.Order;
import java.util.List;

/**
 * 订单仓储接口
 */
public interface OrderRepository {
    Order save(Order order);
    Order findById(Long id);
    List<Order> findByUserId(Long userId);
    List<Order> findByGuestId(String guestId);
    List<Order> findAll();
    void updateStatus(Long orderId, Order.OrderStatus status);
}
