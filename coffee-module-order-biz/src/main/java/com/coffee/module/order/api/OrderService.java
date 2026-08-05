package com.coffee.module.order.api;

import com.coffee.module.order.api.dto.*;

/**
 * 订单服务 API
 */
public interface OrderService {

    /**
     * 创建订单
     */
    OrderResponse createOrder(CreateOrderCommand command);

    /**
     * 更新订单状态
     */
    OrderResponse updateOrderStatus(Long orderId, String action, boolean isUserOrder);

    /**
     * 获取用户订单列表
     */
    java.util.List<java.util.Map<String, Object>> getUserOrders(Long userId);

    /**
     * 获取游客订单列表
     */
    java.util.List<java.util.Map<String, Object>> getGuestOrders(String guestId);

    /**
     * 获取所有订单
     */
    java.util.List<java.util.Map<String, Object>> getAllOrders();

    /**
     * 获取菜单信息
     */
    java.util.Map<String, Object> getMenuInfo();
}
