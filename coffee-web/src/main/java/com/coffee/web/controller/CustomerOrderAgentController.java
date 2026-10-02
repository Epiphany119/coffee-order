package com.coffee.web.controller;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.module.customeragent.api.CustomerOrderAgentService;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderItem;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.api.dto.CartItemCommand;
import com.coffee.module.order.api.dto.CreateOrderCommand;
import com.coffee.module.order.api.dto.OrderResponse;
import com.coffee.module.payment.api.PaymentService;
import com.coffee.module.payment.api.dto.PaymentResponse;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.web.agent.AgentRunAuditService;
import com.coffee.web.idempotency.OrderIdempotencyService;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** HTTP 适配层：计划与确认分离；支持 SSE 流式响应防止超时。 */
@RestController
@RequestMapping("/api/customer-agent")
public class CustomerOrderAgentController {
    private final CustomerOrderAgentService customerOrderAgentService;
    private final OrderService orderService;
    private final OrderIdempotencyService idempotencyService;
    private final DeliveryService deliveryService;
    private final StoreService storeService;
    private final PaymentService paymentService;
    private final AgentRunAuditService agentAudit;
    private final ZhipuChatClient chatClient;

    public CustomerOrderAgentController(CustomerOrderAgentService customerOrderAgentService,
                                        OrderService orderService,
                                        OrderIdempotencyService idempotencyService,
                                        DeliveryService deliveryService,
                                        StoreService storeService,
                                        PaymentService paymentService,
                                        AgentRunAuditService agentAudit,
                                        ZhipuChatClient chatClient) {
        this.customerOrderAgentService = customerOrderAgentService;
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.deliveryService = deliveryService;
        this.storeService = storeService;
        this.paymentService = paymentService;
        this.agentAudit = agentAudit;
        this.chatClient = chatClient;
    }
    
    @PostMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestBody CustomerAgentRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择门店后再与点单 Agent 对话");
        RequestIdentity requestIdentity = AccessGuard.currentIdentity();
        Identity identity = currentCustomerIdentity();
        String runId = agentAudit.start(requestIdentity, "customer_order", request.storeId, null, request.message);
        long started = System.nanoTime();
        chatClient.beginUsageTracking();
        try {
            Map<String, Object> raw = customerOrderAgentService.plan(
                    request.storeId, identity.userId(), identity.guestId(), request.message);
            recordPlan(runId, request.storeId, raw, elapsedMs(started));
            Map<String, Object> result = new LinkedHashMap<>(raw);
            result.put("runId", runId);
            agentAudit.markPlanReady(runId);
            return Result.success(result);
        } catch (RuntimeException ex) {
            agentAudit.recordStep(runId, 0, "customer_agent_plan", true, "FAILED", elapsedMs(started), 0,
                    Map.of("storeId", request.storeId), safeError(ex));
            agentAudit.finish(requestIdentity, runId, "FAILED", null, safeError(ex), 1);
            throw ex;
        } finally {
            agentAudit.recordModelUsage(runId, chatClient.endUsageTracking());
        }
    }

    /**
     * 流式 plan 端点。当前方案生成服务没有可供 SSE 订阅的细粒度阶段回调，
     * 因此只发送处理中提示，完成后发送结果，不虚报内部阶段或进度。
     */
    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(@RequestBody CustomerAgentRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择门店");
        RequestIdentity requestIdentity = AccessGuard.currentIdentity();
        Identity identity = currentCustomerIdentity();
        String runId = agentAudit.start(requestIdentity, "customer_order", request.storeId, null, request.message);

        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            long started = System.nanoTime();
            chatClient.beginUsageTracking();
            try {
                // Send the only truthful in-flight status.
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "processing",
                        "message", "正在为你生成点单方案")));
                Map<String, Object> raw = customerOrderAgentService.plan(
                        request.storeId, identity.userId(), identity.guestId(), request.message);
                recordPlan(runId, request.storeId, raw, elapsedMs(started));
                Map<String, Object> result = new LinkedHashMap<>(raw);
                result.put("runId", runId);

                // Report completion after plan generation returns.
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "done",
                        "progress", 100,
                        "message", "点单方案已生成")));
                emitter.send(SseEmitter.event().name("result").data(result));
                agentAudit.markPlanReady(runId);
                emitter.complete();
            } catch (Exception e) {
                agentAudit.recordStep(runId, 0, "customer_agent_plan", true, "FAILED", elapsedMs(started), 0,
                        Map.of("storeId", request.storeId), safeError(e));
                agentAudit.finish(requestIdentity, runId, "FAILED", null, safeError(e), 1);
                try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", e instanceof ServiceException && e.getMessage() != null
                                    ? e.getMessage() : "Agent 服务暂时不可用，请稍后重试",
                            "step", "failed")));
                } catch (IOException ignored) { }
                emitter.completeWithError(e);
            } finally {
                agentAudit.recordModelUsage(runId, chatClient.endUsageTracking());
            }
        });
        return emitter;
    }

    @PostMapping("/plans/confirm")
    public Result<OrderResponse> confirm(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ConfirmPlanRequest request) {
        if (request == null || request.storeId == null || request.planToken == null || request.planToken.isBlank()) {
            throw new ServiceException(400, "缺少 Agent 方案确认信息");
        }
        String fulfillmentType = normalizeFulfillment(request.fulfillmentType);
        RequestIdentity requestIdentity = AccessGuard.currentIdentity();
        Identity identity = currentCustomerIdentity();
        long started = System.nanoTime();
        try {
            if ("DELIVERY".equals(fulfillmentType)) {
                if (identity.userId() == null) {
                    throw new ServiceException(400, "外卖配送需要先登录顾客账号");
                }
                if (request.deliveryAddressId == null || request.deliveryAddressId <= 0) {
                    throw new ServiceException(400, "外卖配送请选择收货地址");
                }
            }
            AgentConfirmFingerprint fingerprint = new AgentConfirmFingerprint(
                    request.planToken, request.storeId, fulfillmentType,
                    "DELIVERY".equals(fulfillmentType) ? request.deliveryAddressId : null,
                    request.includeAddOn);
            OrderResponse response = idempotencyService.executeWithRequest(
                    idempotencyKey, identity.userId(), identity.guestId(), fingerprint, () -> {
                        AgentOrderPlan plan = customerOrderAgentService.confirm(request.planToken, idempotencyKey,
                                request.storeId, identity.userId(), identity.guestId(), request.includeAddOn);
                        CreateOrderCommand command = new CreateOrderCommand();
                        command.setStoreId(plan.storeId());
                        command.setUserId(plan.userId());
                        command.setGuestId(plan.guestId());
                        command.setFulfillmentType(fulfillmentType);
                        command.setDeliveryAddressId("DELIVERY".equals(fulfillmentType) ? request.deliveryAddressId : null);
                        command.setNote(plan.note());
                        command.setItems(plan.items().stream().map(line -> {
                            CartItemCommand item = new CartItemCommand();
                            item.setProductCode(line.productCode());
                            item.setSize(line.size());
                            item.setQuantity(line.quantity());
                            item.setCondiments(List.of());
                            return item;
                        }).toList());
                        if (plan.userId() != null && plan.userId() > 0) command.setCouponCode("AGENT_FIKA8");
                        OrderResponse created = orderService.createOrder(command);
                        if ("DELIVERY".equals(command.getFulfillmentType())) {
                            StoreResponse store = storeService.getStore(command.getStoreId());
                            DeliveryOrderCreateRequest deliveryRequest = new DeliveryOrderCreateRequest();
                            deliveryRequest.setOrderId(created.getOrderId());
                            deliveryRequest.setOrderNo(created.getOrderNo());
                            deliveryRequest.setUserId(command.getUserId());
                            deliveryRequest.setStoreId(command.getStoreId());
                            deliveryRequest.setStoreName(store.getName());
                            deliveryRequest.setAmount(created.getFinalPrice());
                            deliveryRequest.setItemSummary(created.getOrderName());
                            deliveryRequest.setItems(toDeliveryItems(created));
                            deliveryRequest.setAddressId(command.getDeliveryAddressId());
                            deliveryRequest.setNote(command.getNote());
                            DeliveryOrderResponse delivery = deliveryService.createDeliveryOrder(deliveryRequest);
                            created.setDeliveryOrderId(delivery.getDeliveryOrderId());
                        }
                        PaymentResponse payment = paymentService.createForOrder(created.getOrderId());
                        created.setPaymentNo(payment.getPaymentNo());
                        return created;
                    });
            recordOwnedConfirm(requestIdentity, request.runId, response, elapsedMs(started), true, null);
            return Result.success(response);
        } catch (RuntimeException ex) {
            recordOwnedConfirm(requestIdentity, request == null ? null : request.runId, null,
                    elapsedMs(started), false, safeError(ex));
            throw ex;
        }
    }

    private void recordPlan(String runId, Long storeId, Map<String, Object> raw, long latencyMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", "customer_order_plan");
        summary.put("storeId", storeId);
        summary.put("storeName", raw.get("storeName"));
        summary.put("engine", raw.get("engine"));
        summary.put("understanding", raw.get("understanding"));
        summary.put("items", raw.get("items"));
        Object options = raw.get("options");
        summary.put("optionsCount", options instanceof List<?> list ? list.size() : 0);
        summary.put("expiresInSeconds", raw.get("expiresInSeconds"));
        String engine = raw.get("engine") == null ? null : String.valueOf(raw.get("engine"));
        agentAudit.recordPlanJson(runId, summary, engine, fallbackReason(engine));
        Object items = raw.get("items");
        int resultCount = items instanceof List<?> list ? list.size() : 0;
        agentAudit.recordStep(runId, 0, "customer_agent_plan", true, "SUCCEEDED", latencyMs, resultCount,
                Map.of("storeId", storeId), engine == null ? "已生成受控点单方案" : engine);
    }

    private void recordOwnedConfirm(RequestIdentity identity, String runId, OrderResponse response,
                                    long latencyMs, boolean success, String error) {
        if (!AgentRunAuditService.isValidRunId(runId)) return;
        Long orderId = response == null ? null : response.getOrderId();
        agentAudit.recordOwnedStep(identity, runId, 1, "order_create_and_payment", false,
                success ? "SUCCEEDED" : "FAILED", latencyMs, success ? 1 : 0, Map.of(),
                success ? "订单、外卖单（如有）和支付单已进入正式链路"
                        : (error == null ? "订单创建失败" : error));
        agentAudit.recordOrderResult(identity, runId, success, orderId);
        agentAudit.finish(identity, runId, success ? "ORDER_SUCCEEDED" : "ORDER_FAILED",
                success ? "订单已创建" : null, error, 2, success, orderId);
    }

    private String fallbackReason(String engine) {
        if (engine == null || engine.isBlank()) return "customer_agent_unknown_route";
        String normalized = engine.toLowerCase(Locale.ROOT);
        if (normalized.contains("rule-tools")) return "rule_engine_fallback";
        if (normalized.contains("exact-match")) return "exact_match_fallback";
        if (normalized.contains("llm-combo")) return "llm_combo_fallback";
        if (normalized.contains("llm-select")) return null;
        return "customer_agent_route";
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
    }

    private Identity currentCustomerIdentity() {
        RequestIdentity identity = AccessGuard.currentIdentity();
        if (identity.kind() == RequestIdentity.Kind.USER && identity.id() != null) return new Identity(identity.id(), null);
        if (identity.kind() == RequestIdentity.Kind.GUEST && identity.guestId() != null && !identity.guestId().isBlank()) return new Identity(null, identity.guestId());
        throw new ServiceException(403, "点单 Agent 仅支持顾客或游客身份");
    }

    private String normalizeFulfillment(String raw) {
        String fulfillment = raw == null || raw.isBlank()
                ? "PICKUP" : raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PICKUP", "DINE_IN", "DELIVERY").contains(fulfillment)) {
            throw new ServiceException(400, "不支持的取餐方式");
        }
        return fulfillment;
    }

    private List<DeliveryOrderItem> toDeliveryItems(OrderResponse order) {
        if (order == null || order.getItems() == null) return List.of();
        return order.getItems().stream().map(item -> {
            DeliveryOrderItem snapshot = new DeliveryOrderItem();
            snapshot.setProductCode(item.getProductCode());
            snapshot.setBeverageName(item.getBeverageName());
            snapshot.setImageUrl(item.getImageUrl());
            snapshot.setSize(item.getSize());
            snapshot.setCondiments(item.getCondiments());
            snapshot.setQuantity(item.getQuantity());
            snapshot.setUnitPrice(item.getUnitPrice());
            snapshot.setOriginalUnitPrice(item.getOriginalUnitPrice());
            snapshot.setSubtotal(item.getSubtotal());
            return snapshot;
        }).toList();
    }

    private record Identity(Long userId, String guestId) { }
    private record AgentConfirmFingerprint(String planToken, Long storeId, String fulfillmentType,
                                           Long deliveryAddressId, boolean includeAddOn) { }
    public static class CustomerAgentRequest { public Long storeId; public Long userId; public String guestId; public String message; }
    public static class ConfirmPlanRequest { public String planToken; public String runId; public Long storeId; public Long userId; public String guestId; public String fulfillmentType; public Long deliveryAddressId; public boolean includeAddOn; }
}
