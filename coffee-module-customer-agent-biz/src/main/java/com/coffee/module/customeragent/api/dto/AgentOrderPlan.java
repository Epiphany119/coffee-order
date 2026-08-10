package com.coffee.module.customeragent.api.dto;

import java.util.List;

/**
 * 一次性确认的 Agent 订单计划。
 * 基础商品和可选凑单商品都封装在同一服务端对象中，浏览器始终只持有一个计划令牌。
 */
public record AgentOrderPlan(Long storeId, Long userId, String guestId, List<AgentOrderLine> items,
                             List<AgentOrderLine> addOnItems, String note) {
    public AgentOrderPlan(Long storeId, Long userId, String guestId, List<AgentOrderLine> items, String note) {
        this(storeId, userId, guestId, items, List.of(), note);
    }
    public List<AgentOrderLine> resolvedItems(boolean includeAddOn) {
        if (!includeAddOn || addOnItems == null || addOnItems.isEmpty()) return items;
        java.util.ArrayList<AgentOrderLine> all = new java.util.ArrayList<>(items);
        all.addAll(addOnItems);
        return List.copyOf(all);
    }
}
