package com.coffee.web.controller;

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
import com.coffee.web.idempotency.OrderIdempotencyService;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    public CustomerOrderAgentController(CustomerOrderAgentService customerOrderAgentService,
                                        OrderService orderService,
                                        OrderIdempotencyService idempotencyService,
                                        DeliveryService deliveryService,
                                        StoreService storeService,
                                        PaymentService paymentService) {
        this.customerOrderAgentService = customerOrderAgentService;
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.deliveryService = deliveryService;
        this.storeService = storeService;
        this.paymentService = paymentService;
    }
    
    @PostMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestBody CustomerAgentRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择门店后再与点单 Agent 对话");
        Identity identity = currentCustomerIdentity();
        return Result.success(customerOrderAgentService.plan(request.storeId, identity.userId(), identity.guestId(), request.message));
    }

    /**
     * 流式 plan 端点 — SSE 逐步推送阶段状态和进度，防止 15s timeout
     *
     * 推送阶段：
     * 1. intent_parsing (15%)    → "正在理解你的需求"
     * 2. candidate_search (35%)  → "正在从菜单中搜索匹配的商品"
     * 3. llm_selection (60%)     → "正在为你组合最优搭配"
     * 4. integrating (85%)       → "正在整合最终结果"
     * 5. done (100%)             → 推送最终结果
     *
     * 前端可以根据 stage.step 渲染不同的 loading 动画和文案
     */
    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(@RequestBody CustomerAgentRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择门店");
        Identity identity = currentCustomerIdentity();

        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                // 阶段 1: 意图解析
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "intent_parsing",
                        "progress", 15,
                        "message", "正在理解你的需求…")));

                // 阶段 2: 候选集查询
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "candidate_search",
                        "progress", 35,
                        "message", "正在从菜单中搜索匹配的商品…")));

                // 阶段 3: LLM 选择
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "llm_selection",
                        "progress", 60,
                        "message", "正在为你组合最优搭配…")));

                // 执行实际 plan
                Map<String, Object> result = customerOrderAgentService.plan(
                        request.storeId, identity.userId(), identity.guestId(), request.message);

                // 阶段 4: 整合结果
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "integrating",
                        "progress", 85,
                        "message", "正在整合最终结果…")));

                // 阶段 5: 完成
                emitter.send(SseEmitter.event().name("stage").data(Map.of(
                        "step", "done",
                        "progress", 100,
                        "message", "已为你配好！")));
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", e instanceof ServiceException && e.getMessage() != null
                                    ? e.getMessage() : "Agent 服务暂时不可用，请稍后重试",
                            "step", "failed")));
                } catch (IOException ignored) { }
                emitter.completeWithError(e);
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
        Identity identity = currentCustomerIdentity();
        if ("DELIVERY".equals(fulfillmentType)) {
            if (identity.userId() == null) {
                throw new ServiceException(400, "外卖配送需要先登录顾客账号");
            }
            if (request.deliveryAddressId == null || request.deliveryAddressId <= 0) {
                throw new ServiceException(400, "外卖配送请选择收货地址");
            }
        }
        AgentOrderPlan plan = customerOrderAgentService.confirm(request.planToken, idempotencyKey, request.storeId, identity.userId(), identity.guestId(), request.includeAddOn);
        CreateOrderCommand command = new CreateOrderCommand();
        command.setStoreId(plan.storeId()); command.setUserId(plan.userId()); command.setGuestId(plan.guestId());
        command.setFulfillmentType(fulfillmentType);
        command.setDeliveryAddressId("DELIVERY".equals(fulfillmentType) ? request.deliveryAddressId : null);
        command.setNote(plan.note());
        command.setItems(plan.items().stream().map(line -> { CartItemCommand item = new CartItemCommand(); item.setProductCode(line.productCode()); item.setSize(line.size()); item.setQuantity(line.quantity()); item.setCondiments(List.of()); return item; }).toList());
        if (plan.userId() != null && plan.userId() > 0) command.setCouponCode("AGENT_FIKA8");
        OrderResponse response = idempotencyService.execute(idempotencyKey, plan.userId(), plan.guestId(), command, () -> {
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
        return Result.success(response);
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
    public static class CustomerAgentRequest { public Long storeId; public Long userId; public String guestId; public String message; }
    public static class ConfirmPlanRequest { public String planToken; public Long storeId; public Long userId; public String guestId; public String fulfillmentType; public Long deliveryAddressId; public boolean includeAddOn; }
}
