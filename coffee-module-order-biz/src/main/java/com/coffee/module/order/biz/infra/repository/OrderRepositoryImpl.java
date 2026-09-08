package com.coffee.module.order.biz.infra.repository;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.OrderItem;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.coffee.module.order.biz.infra.persistence.OrderItemMapper;
import com.coffee.module.order.biz.infra.persistence.OrderItemPO;
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
    private final OrderItemMapper orderItemMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public Order save(Order order) {
        OrderPO po = toPO(order);
        orderMapper.insert(po);
        order.setId(po.getId());
        // 级联保存订单明细（order_item），1 订单 N 明细
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                OrderItemPO itemPo = new OrderItemPO();
                itemPo.setOrderId(order.getId());
                itemPo.setProductId(item.getProductId());
                itemPo.setProductName(item.getBeverageName());
                itemPo.setImageUrl(item.getImageUrl());
                itemPo.setQuantity(item.getQuantity());
                itemPo.setUnitPrice(item.getUnitPrice());
                itemPo.setOriginalUnitPrice(item.getOriginalUnitPrice());
                itemPo.setSubtotal(item.getSubtotal());
                orderItemMapper.insert(itemPo);
            }
        }
        return order;
    }

    @Override
    public Order findById(Long id) {
        OrderPO po = orderMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public List<OrderItem> findItemsByOrderId(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId).stream()
                .map(this::toItemDomain)
                .collect(Collectors.toList());
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
    public List<Order> findByStoreId(Long storeId) {
        return orderMapper.selectByStoreId(storeId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStoreIdAndStatus(Long storeId, String status) {
        return orderMapper.selectByStoreIdAndStatus(storeId, status).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findRecentByStoreId(Long storeId, int limit) {
        return orderMapper.selectRecentByStoreId(storeId, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Map<String, Object> todayStats(Long storeId) {
        return orderMapper.selectTodayStats(storeId);
    }

    @Override
    public long countPendingByStoreId(Long storeId) {
        return orderMapper.countPendingByStoreId(storeId);
    }

    @Override
    public int maxSeqOfDay(Long storeId, String datePrefix) {
        return orderMapper.selectMaxSeq(storeId, datePrefix);
    }

    @Override
    public List<java.util.Map<String, Object>> salesDaily(Long storeId, int days) {
        java.util.Map<String, Object> byDay = new java.util.HashMap<>();
        for (java.util.Map<String, Object> r : orderMapper.selectDailySales(storeId, days)) {
            byDay.put(String.valueOf(r.get("day")), r.get("amount"));
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        java.time.LocalDate today = java.time.LocalDate.now();
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String key = today.minusDays(i).format(fmt);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("day", key);
            m.put("amount", ((Number) byDay.getOrDefault(key, 0)).doubleValue());
            out.add(m);
        }
        return out;
    }

    @Override
    public List<java.util.Map<String, Object>> salesWeekly(Long storeId, int weeks) {
        java.util.Map<String, Object> byWeek = new java.util.HashMap<>();
        for (java.util.Map<String, Object> r : orderMapper.selectWeeklySales(storeId, weeks)) {
            byWeek.put(String.valueOf(r.get("day")), r.get("amount"));
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        java.time.LocalDate thisMonday = java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            String key = thisMonday.minusWeeks(i).format(fmt);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("day", key);
            m.put("amount", ((Number) byWeek.getOrDefault(key, 0)).doubleValue());
            out.add(m);
        }
        return out;
    }

    @Override
    public List<java.util.Map<String, Object>> hotProducts(Long storeId, int days, int limit) {
        return orderItemMapper.selectHotProducts(storeId, days, limit);
    }

    @Override
    public void updateStatus(Long orderId, Order.OrderStatus status) {
        orderMapper.updateStatus(orderId, status.name());
    }

    @Override
    public boolean updateStatusIfCurrent(Long orderId, Order.OrderStatus expectedStatus, Order.OrderStatus status) {
        return orderMapper.updateStatusIfCurrent(orderId, expectedStatus.name(), status.name()) > 0;
    }

    private Order toDomain(OrderPO po) {
        Order order = new Order();
        order.setId(po.getId());
        order.setUserId(po.getUserId());
        order.setGuestId(po.getGuestId());
        order.setStoreId(po.getStoreId());
        order.setFulfillmentType(po.getFulfillmentType());
        order.setNote(po.getNote());
        order.setBeverageName(po.getBeverageName());
        order.setSize(po.getSize());
        order.setCustomSize(po.getCustomSize());
        order.setCondiments(po.getCondiments());
        order.setOriginalPrice(po.getOriginalPrice());
        order.setFinalPrice(po.getFinalPrice());
        order.setVoucherNo(po.getVoucherNo());
        order.setStatus(Order.OrderStatus.valueOf(po.getStatus()));
        order.setCreatedAt(po.getCreatedAt());
        order.setEstimatedReadyTime(po.getEstimatedReadyTime());
        order.setOrderNo(po.getOrderNo());
        return order;
    }

    private OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.getId());
        po.setUserId(order.getUserId());
        po.setGuestId(order.getGuestId());
        po.setStoreId(order.getStoreId());
        po.setFulfillmentType(order.getFulfillmentType());
        po.setNote(order.getNote());
        po.setBeverageName(order.getBeverageName());
        po.setSize(order.getSize());
        po.setCustomSize(order.getCustomSize());
        po.setCondiments(order.getCondiments());
        po.setOriginalPrice(order.getOriginalPrice());
        po.setFinalPrice(order.getFinalPrice());
        po.setVoucherNo(order.getVoucherNo());
        po.setStatus(order.getStatus().name());
        po.setCreatedAt(order.getCreatedAt());
        po.setEstimatedReadyTime(order.getEstimatedReadyTime());
        po.setOrderNo(order.getOrderNo());
        return po;
    }

    private OrderItem toItemDomain(OrderItemPO po) {
        OrderItem item = new OrderItem();
        item.setProductId(po.getProductId());
        item.setBeverageName(po.getProductName());
        item.setImageUrl(po.getImageUrl());
        item.setQuantity(po.getQuantity());
        item.setUnitPrice(po.getUnitPrice());
        item.setOriginalUnitPrice(po.getOriginalUnitPrice());
        item.setSubtotal(po.getSubtotal());
        return item;
    }
}
