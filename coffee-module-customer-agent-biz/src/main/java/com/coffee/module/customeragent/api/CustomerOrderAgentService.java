package com.coffee.module.customeragent.api;

import java.util.List;
import java.util.Map;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;

/** 顾客点单 Agent：生成受控方案，并提供绑定身份的一次性确认快照。 */
public interface CustomerOrderAgentService {
    Map<String, Object> plan(Long storeId, Long userId, String guestId, String message);
    /** 按顾客显式编辑后的规格、数量和配料重建方案；可选地在原方案基础上追加，合并为一笔支付。 */
    Map<String, Object> rebuild(Long storeId, Long userId, String guestId, String basePlanToken,
                                List<Map<String, Object>> lines, boolean replace, String note);
    /** 读取当前方案快照用于聊天内二次确认；不消费令牌、不创建订单。 */
    Map<String, Object> view(String planToken, Long storeId, Long userId, String guestId);
    AgentOrderPlan confirm(String planToken, String idempotencyKey, Long storeId, Long userId, String guestId, boolean includeAddOn);
}
