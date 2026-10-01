package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.module.merchantagent.api.MerchantGrowthAgentService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.web.agent.AgentBusinessMetricsService;
import com.coffee.web.agent.AgentKnowledgeService;
import com.coffee.web.agent.MenuKnowledgeBootstrapService;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant/{merchantId}/growth-agent")
public class GrowthAgentController {
    private final StoreService storeService;
    private final MerchantGrowthAgentService growthAgentService;
    private final AgentBusinessMetricsService metricsService;
    private final AgentKnowledgeService knowledgeService;
    private final MenuKnowledgeBootstrapService menuKnowledgeBootstrap;

    public GrowthAgentController(StoreService storeService, MerchantGrowthAgentService growthAgentService,
                                 AgentBusinessMetricsService metricsService, AgentKnowledgeService knowledgeService,
                                 MenuKnowledgeBootstrapService menuKnowledgeBootstrap) {
        this.storeService = storeService;
        this.growthAgentService = growthAgentService;
        this.metricsService = metricsService;
        this.knowledgeService = knowledgeService;
        this.menuKnowledgeBootstrap = menuKnowledgeBootstrap;
    }

    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@PathVariable Long merchantId, @RequestBody AgentQuestion request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new ServiceException(400, "Question is required");
        }
        StoreResponse ownedStore = store(merchantId, request.storeId);
        List<AgentKnowledgeService.KnowledgeHit> knowledgeHits =
                knowledgeService.retrieveForMerchant(request.message, ownedStore.getStoreId(), 4);
        String knowledgeContext = knowledgeHits.stream()
                .map(hit -> hit.title() + ": " + hit.content())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        Map<String, Object> result = growthAgentService.analyze(
                merchantId, ownedStore.getStoreId(), request.message, knowledgeContext, true);
        result.put("knowledgeSources", knowledgeHits.stream().map(AgentKnowledgeService.KnowledgeHit::title).toList());
        result.put("storeId", ownedStore.getStoreId());
        result.put("storeName", ownedStore.getName());
        return Result.success(result);
    }

    @PostMapping("/actions/{actionId}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long merchantId, @PathVariable Long actionId,
                                               @RequestBody ConfirmProposalRequest request) {
        StoreResponse ownedStore = store(merchantId, request == null ? null : request.storeId);
        Integer version = request == null ? null : request.proposalVersion;
        return Result.success(growthAgentService.confirmAction(merchantId, ownedStore.getStoreId(), actionId, version));
    }

    @PostMapping("/actions/{actionId}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long merchantId, @PathVariable Long actionId,
                                                @RequestParam(required = false) Long storeId) {
        StoreResponse ownedStore = store(merchantId, storeId);
        return Result.success(growthAgentService.executeAction(merchantId, ownedStore.getStoreId(), actionId));
    }

    @GetMapping("/actions")
    public Result<List<Map<String, Object>>> list(@PathVariable Long merchantId,
                                                   @RequestParam(required = false) Long storeId,
                                                   @RequestParam(defaultValue = "20") int limit) {
        StoreResponse ownedStore = store(merchantId, storeId);
        return Result.success(growthAgentService.listActions(merchantId, ownedStore.getStoreId(), limit));
    }

    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics(@PathVariable Long merchantId,
                                                @RequestParam(required = false) Long storeId,
                                                @RequestParam(defaultValue = "7") int days) {
        StoreResponse ownedStore = store(merchantId, storeId);
        return Result.success(metricsService.summarize(
                AccessGuard.currentIdentity(), ownedStore.getStoreId(), days));
    }

    @PostMapping("/knowledge/documents")
    public Result<Map<String, Object>> upsertKnowledge(@PathVariable Long merchantId,
                                                        @RequestBody KnowledgeRequest request) {
        if (request == null || request.title == null || request.content == null) {
            throw new ServiceException(400, "Document title and content are required");
        }
        StoreResponse ownedStore = store(merchantId, request.storeId);
        String visibility = request.visibility == null || request.visibility.isBlank()
                ? "MERCHANT_INTERNAL" : request.visibility;
        knowledgeService.upsert(ownedStore.getStoreId(), request.title, request.content, request.source, visibility);
        return Result.success(Map.of("accepted", true, "visibility", visibility));
    }

    @PostMapping("/knowledge/bootstrap/menu")
    public Result<Map<String, Object>> bootstrapMenuKnowledge(@PathVariable Long merchantId,
                                                               @RequestBody(required = false) StoreRequest request) {
        StoreResponse ownedStore = store(merchantId, request == null ? null : request.storeId);
        menuKnowledgeBootstrap.bootstrapAsync(ownedStore.getStoreId());
        return Result.success(Map.of("accepted", true, "message", "Menu knowledge sync was queued",
                "storeId", ownedStore.getStoreId()));
    }

    private StoreResponse store(Long merchantId, Long requestedStoreId) {
        AccessGuard.requireMerchant(merchantId);
        List<StoreResponse> stores = storeService.listByMerchant(merchantId);
        if (stores.isEmpty()) throw new ServiceException(404, "Merchant has no store");
        if (requestedStoreId == null) return stores.get(0);
        return stores.stream().filter(item -> requestedStoreId.equals(item.getStoreId())).findFirst()
                .orElseThrow(() -> new ServiceException(403, "Store is not owned by this merchant"));
    }

    public static class AgentQuestion { public Long storeId; public String message; }
    public static class ConfirmProposalRequest { public Long storeId; public Integer proposalVersion; }
    public static class StoreRequest { public Long storeId; }
    public static class KnowledgeRequest {
        public Long storeId;
        public String title;
        public String content;
        public String source;
        public String visibility;
    }
}
