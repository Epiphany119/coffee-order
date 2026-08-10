package com.coffee.module.customeragent.api;

import java.util.Map;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;

/** 顾客点单 Agent：生成受控方案，并提供绑定身份的一次性确认快照。 */
public interface CustomerOrderAgentService {
    Map<String, Object> plan(Long storeId, Long userId, String guestId, String message);
    AgentOrderPlan confirm(String planToken, String idempotencyKey, Long storeId, Long userId, String guestId, boolean includeAddOn);
}
