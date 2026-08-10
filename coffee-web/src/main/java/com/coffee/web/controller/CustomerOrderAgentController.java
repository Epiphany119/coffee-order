package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.module.customeragent.api.CustomerOrderAgentService;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.api.dto.CartItemCommand;
import com.coffee.module.order.api.dto.CreateOrderCommand;
import com.coffee.module.order.api.dto.OrderResponse;
import com.coffee.web.idempotency.OrderIdempotencyService;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** HTTP 适配层：计划与确认分离；确认只消费服务端签发的一次性计划令牌。 */
@RestController
@RequestMapping("/api/customer-agent")
public class CustomerOrderAgentController {
    private final CustomerOrderAgentService customerOrderAgentService;
    private final OrderService orderService; private final OrderIdempotencyService idempotencyService;
    public CustomerOrderAgentController(CustomerOrderAgentService customerOrderAgentService, OrderService orderService, OrderIdempotencyService idempotencyService) { this.customerOrderAgentService = customerOrderAgentService; this.orderService = orderService; this.idempotencyService = idempotencyService; }
    @PostMapping("/plan")
    public Result<Map<String, Object>> plan(@RequestBody CustomerAgentRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择门店后再与点单 Agent 对话");
        Identity identity = currentCustomerIdentity();
        return Result.success(customerOrderAgentService.plan(request.storeId, identity.userId(), identity.guestId(), request.message));
    }

    @PostMapping("/plans/confirm")
    public Result<OrderResponse> confirm(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody ConfirmPlanRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "缺少 Agent 方案确认信息");
        // 订单归属只信任已验签的 Bearer Token，绝不信任浏览器 body 里的 userId/guestId。
        // 这样即使页面曾残留游客参数，登录用户的订单也不会再被记到游客名下。
        Identity identity = currentCustomerIdentity();
        AgentOrderPlan plan = customerOrderAgentService.confirm(request.planToken, idempotencyKey, request.storeId, identity.userId(), identity.guestId(), request.includeAddOn);
        CreateOrderCommand command = new CreateOrderCommand();
        command.setStoreId(plan.storeId()); command.setUserId(plan.userId()); command.setGuestId(plan.guestId());
        command.setFulfillmentType("DINE_IN".equalsIgnoreCase(request.fulfillmentType) ? "DINE_IN" : "PICKUP");
        command.setNote(plan.note());
        command.setItems(plan.items().stream().map(line -> { CartItemCommand item = new CartItemCommand(); item.setProductCode(line.productCode()); item.setSize(line.size()); item.setQuantity(line.quantity()); item.setCondiments(List.of()); return item; }).toList());
        // Agent 直达支付与普通购物车共享优惠口径：已登录用户达到门槛时自动应用平台基础满减。
        // 游客不享受会员/固定权益，且后续订单服务仍会在服务端复核价格和门槛。
        if (plan.userId() != null && plan.userId() > 0) command.setCouponCode("AGENT_FIKA8");
        // 订单接口只负责创建订单；支付单由收银台按 orderId 独立获取/创建，避免异步时序互相覆盖。
        OrderResponse response = idempotencyService.execute(idempotencyKey, plan.userId(), plan.guestId(), command, () -> orderService.createOrder(command));
        return Result.success(response);
    }

    private Identity currentCustomerIdentity() {
        RequestIdentity identity = AccessGuard.currentIdentity();
        if (identity.kind() == RequestIdentity.Kind.USER && identity.id() != null) return new Identity(identity.id(), null);
        if (identity.kind() == RequestIdentity.Kind.GUEST && identity.guestId() != null && !identity.guestId().isBlank()) return new Identity(null, identity.guestId());
        throw new ServiceException(403, "点单 Agent 仅支持顾客或游客身份");
    }
    private record Identity(Long userId, String guestId) { }
    public static class CustomerAgentRequest { public Long storeId; public Long userId; public String guestId; public String message; }
    public static class ConfirmPlanRequest { public String planToken; public Long storeId; public Long userId; public String guestId; public String fulfillmentType; public boolean includeAddOn; }
}
