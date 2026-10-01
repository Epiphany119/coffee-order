package com.coffee.module.merchantagent.api;

import java.util.List;
import java.util.Map;

public interface MerchantGrowthAgentService {
    Map<String, Object> analyze(Long merchantId, Long storeId, String question, String knowledgeContext, boolean prepareAction);
    Map<String, Object> confirmAction(Long merchantId, Long storeId, Long actionId, Integer proposalVersion);
    Map<String, Object> executeAction(Long merchantId, Long storeId, Long actionId);
    List<Map<String, Object>> listActions(Long merchantId, Long storeId, int limit);
}
