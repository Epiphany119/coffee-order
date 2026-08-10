package com.coffee.module.merchantagent.api;

import java.util.List;
import java.util.Map;

/** 店长增长 Agent 应用 API：诊断、待审批营销动作与审计。 */
public interface MerchantGrowthAgentService {
    Map<String, Object> analyze(Long storeId, String question);
    Long createAction(Long merchantId, Long storeId, String actionType, String title, Map<String, Object> proposal);
    Map<String, Object> executeAction(Long merchantId, Long storeId, Long actionId);
    List<Map<String, Object>> listActions(Long merchantId, Long storeId, int limit);
}
