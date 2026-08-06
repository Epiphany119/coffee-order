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
     * 商家操作订单状态（接单/完成/取消，校验订单归属店铺）
     */
    OrderResponse updateStoreOrderStatus(Long orderId, String action, Long storeId);

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
     * 获取店铺订单列表（商家端，可带状态过滤）
     */
    java.util.List<java.util.Map<String, Object>> getStoreOrders(Long storeId, String status);

    /**
     * 获取店铺统计（dashboard：今日营业额/订单数/待处理/近7天）
     */
    java.util.Map<String, Object> getStoreStats(Long storeId);

    /**
     * 获取菜单信息（按店铺）
     */
    java.util.Map<String, Object> getMenuInfo(Long storeId);

    /**
     * 用户累计已省金额（已完成订单 原价-实付 之和，含会员折扣与优惠券）
     */
    double getTotalSaved(Long userId);
}
