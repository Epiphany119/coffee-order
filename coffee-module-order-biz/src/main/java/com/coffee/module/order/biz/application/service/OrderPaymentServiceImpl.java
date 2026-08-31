package com.coffee.module.order.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderPaymentService;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单支付服务实现：UNPAID → PENDING 状态回写
 */
@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderRepository orderRepository;

    public OrderPaymentServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void checkPayable(Long orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (order.getStatus() != Order.OrderStatus.UNPAID) {
            throw new ServiceException(409, "订单当前状态不可支付，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public boolean markPaid(Long orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        // 幂等：只有 UNPAID 才流转到 PENDING；已进入履约的订单仍视为已接受支付结果。
        if (order.getStatus() == Order.OrderStatus.UNPAID) {
            return orderRepository.updateStatusIfCurrent(orderId, Order.OrderStatus.UNPAID, Order.OrderStatus.PENDING);
        }
        return order.getStatus() != Order.OrderStatus.CANCELED;
    }
}
