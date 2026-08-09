package com.coffee.module.order.biz.domain.repository;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.OrderItem;
import java.util.List;

/**
 * 订单仓储接口
 */
public interface OrderRepository {
    Order save(Order order);
    Order findById(Long id);
    /** 订单明细（order_item，按 order_id 查） */
    List<OrderItem> findItemsByOrderId(Long orderId);
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
    /** 店铺当日最大订单顺序号（order_no 末段，跨分类连续；无则 0） */
    int maxSeqOfDay(Long storeId, String datePrefix);
    /** 近 N 天每日营业额，缺日补 0（List<Map: day(YYYYMMDD)/amount>） */
    List<java.util.Map<String, Object>> salesDaily(Long storeId, int days);
    /** 近 N 周每周营业额，周一起始，缺周补 0（List<Map: day(YYYYMMDD)/amount>） */
    List<java.util.Map<String, Object>> salesWeekly(Long storeId, int weeks);
    /** 近 N 天热销商品（Map: name/quantity/amount） */
    List<java.util.Map<String, Object>> hotProducts(Long storeId, int days, int limit);
    void updateStatus(Long orderId, Order.OrderStatus status);
}
