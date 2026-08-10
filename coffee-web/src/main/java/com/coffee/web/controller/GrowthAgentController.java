package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.module.merchantagent.api.MerchantGrowthAgentService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP 适配层：商家身份/店铺归属在 Web，增长 Agent 业务在 merchant-agent-biz。 */
@RestController
@RequestMapping("/api/merchant/{merchantId}/growth-agent")
public class GrowthAgentController {
    private final StoreService storeService; private final MerchantGrowthAgentService growthAgentService;
    public GrowthAgentController(StoreService storeService, MerchantGrowthAgentService growthAgentService) { this.storeService=storeService;this.growthAgentService=growthAgentService; }
    @PostMapping("/analyze") public Result<Map<String,Object>> analyze(@PathVariable Long merchantId,@RequestBody AgentQuestion req){StoreResponse s=store(merchantId);Map<String,Object> out=new LinkedHashMap<>(growthAgentService.analyze(s.getStoreId(),req==null?null:req.message));out.put("storeId",s.getStoreId());out.put("storeName",s.getName());return Result.success(out);}
    @PostMapping("/actions") public Result<Map<String,Object>> create(@PathVariable Long merchantId,@RequestBody AgentActionRequest req){StoreResponse s=store(merchantId);if(req==null||req.actionType==null)throw new ServiceException(400,"缺少 Agent 操作类型");Long id=growthAgentService.createAction(merchantId,s.getStoreId(),req.actionType,req.title==null?"FIKA Agent 方案":req.title,req.proposal);return Result.success(Map.of("id",id,"status","PENDING","message","方案已生成，确认后才会执行"));}
    @PostMapping("/actions/{actionId}/execute") public Result<Map<String,Object>> execute(@PathVariable Long merchantId,@PathVariable Long actionId){StoreResponse s=store(merchantId);return Result.success(growthAgentService.executeAction(merchantId,s.getStoreId(),actionId));}
    @GetMapping("/actions") public Result<List<Map<String,Object>>> list(@PathVariable Long merchantId,@RequestParam(defaultValue="20") int limit){StoreResponse s=store(merchantId);return Result.success(growthAgentService.listActions(merchantId,s.getStoreId(),limit));}
    private StoreResponse store(Long merchantId){AccessGuard.requireMerchant(merchantId);List<StoreResponse> stores=storeService.listByMerchant(merchantId);if(stores.isEmpty())throw new ServiceException(404,"请先完成门店入驻");return stores.get(0);}
    public static class AgentQuestion{public String message;} public static class AgentActionRequest{public String actionType;public String title;public Map<String,Object> proposal;}
}
