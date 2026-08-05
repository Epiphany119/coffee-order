package com.coffee.module.order.biz.infra.repository;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.coffee.module.order.biz.infra.persistence.OrderMapper;
import com.coffee.module.order.biz.infra.persistence.OrderPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单仓储实现
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Order save(Order order) {
        OrderPO po = toPO(order);
        orderMapper.insert(po);
        order.setId(po.getId());
        return order;
    }

    @Override
    public Order findById(Long id) {
        OrderPO po = orderMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderMapper.selectByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByGuestId(String guestId) {
        return orderMapper.selectByGuestId(guestId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll() {
        return orderMapper.selectAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(Long orderId, Order.OrderStatus status) {
        orderMapper.updateStatus(orderId, status.name());
    }

    private Order toDomain(OrderPO po) {
        Order order = new Order();
        order.setId(po.getId());
        order.setUserId(po.getUserId());
        order.setGuestId(po.getGuestId());
        order.setBeverageName(po.getBeverageName());
        order.setSize(po.getSize());
        order.setCondiments(po.getCondiments());
        order.setOriginalPrice(po.getOriginalPrice());
        order.setFinalPrice(po.getFinalPrice());
        order.setStatus(Order.OrderStatus.valueOf(po.getStatus()));
        order.setCreatedAt(po.getCreatedAt());
        order.setEstimatedReadyTime(po.getEstimatedReadyTime());
        return order;
    }

    private OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.getId());
        po.setUserId(order.getUserId());
        po.setGuestId(order.getGuestId());
        po.setBeverageName(order.getBeverageName());
        po.setSize(order.getSize());
        po.setCondiments(order.getCondiments());
        po.setOriginalPrice(order.getOriginalPrice());
        po.setFinalPrice(order.getFinalPrice());
        po.setStatus(order.getStatus().name());
        po.setCreatedAt(order.getCreatedAt());
        po.setEstimatedReadyTime(order.getEstimatedReadyTime());
        return po;
    }
}
