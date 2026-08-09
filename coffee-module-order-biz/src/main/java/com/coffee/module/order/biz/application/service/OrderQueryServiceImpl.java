package com.coffee.module.order.biz.application.service;

import com.coffee.module.order.api.OrderQueryService;
import com.coffee.module.order.api.dto.OrderBrief;
import com.coffee.module.order.biz.infra.persistence.OrderItemMapper;
import com.coffee.module.order.biz.infra.persistence.OrderMapper;
import com.coffee.module.order.biz.infra.persistence.OrderPO;
import org.springframework.stereotype.Service;

/**
 * 订单只读查询服务实现（跨模块调用入口）
 */
@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderQueryServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public OrderBrief getOrderBrief(Long orderId) {
        return toBrief(orderMapper.selectById(orderId));
    }

    @Override
    public OrderBrief getOrderBriefForUser(Long orderId, Long userId) {
        OrderBrief brief = getOrderBrief(orderId);
        if (brief == null || brief.getUserId() == null || !brief.getUserId().equals(userId)) {
            return null;
        }
        return brief;
    }

    private OrderBrief toBrief(OrderPO po) {
        if (po == null) return null;
        OrderBrief brief = new OrderBrief();
        brief.setId(po.getId());
        brief.setOrderNo(po.getOrderNo());
        brief.setBeverageName(po.getBeverageName());
        brief.setStatus(po.getStatus());
        brief.setFinalPrice(po.getFinalPrice());
        brief.setUserId(po.getUserId());
        brief.setGuestId(po.getGuestId());
        brief.setStoreId(po.getStoreId());
        // 第一个明细（按 id 升序）的商品 id：反馈记录归属"订单第一个品"
        brief.setFirstProductId(orderItemMapper.selectByOrderId(po.getId()).stream()
                .findFirst().map(i -> i.getProductId()).orElse(null));
        return brief;
    }
}
