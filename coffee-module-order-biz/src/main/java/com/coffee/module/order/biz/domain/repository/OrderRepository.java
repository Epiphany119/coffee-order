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
    /** 按店查全部（商家订单列表） */
    List<Order> findByStoreId(Long storeId);
    /** 按店+状态过滤 */
    List<Order> findByStoreIdAndStatus(Long storeId, String status);
    /** 按店取最近订单（dashboard 用） */
    List<Order> findRecentByStoreId(Long storeId, int limit);
    /** 今日营业额与订单数（Map: revenue/cnt） */
    java.util.Map<String, Object> todayStats(Long storeId);
    /** 待处理（PENDING）订单数 */
    long countPendingByStoreId(Long storeId);
    /** 近7天每日营业额（List<Map: day/amount>） */
    List<java.util.Map<String, Object>> weekStats(Long storeId);
    void updateStatus(Long orderId, Order.OrderStatus status);
}
