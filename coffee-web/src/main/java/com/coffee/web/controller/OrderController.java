package com.coffee.web.controller;

import com.coffee.common.core.result.Result;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.api.dto.CreateOrderCommand;
import com.coffee.module.order.api.dto.CartItemCommand;
import com.coffee.module.order.api.dto.OrderResponse;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberDTO;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.module.payment.api.PaymentService;
import com.coffee.module.payment.api.dto.PaymentResponse;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.idempotency.OrderIdempotencyService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final MemberService memberService;
    private final PaymentService paymentService;
    private final StoreService storeService;
    private final DeliveryService deliveryService;
    private final OrderIdempotencyService orderIdempotencyService;

    public OrderController(OrderService orderService, MemberService memberService, PaymentService paymentService,
                           StoreService storeService, DeliveryService deliveryService,
                           OrderIdempotencyService orderIdempotencyService) {
        this.orderService = orderService;
        this.memberService = memberService;
        this.paymentService = paymentService;
        this.storeService = storeService;
        this.deliveryService = deliveryService;
        this.orderIdempotencyService = orderIdempotencyService;
    }

    @GetMapping("/menu")
    public Result<Map<String, Object>> getMenu(@RequestParam(value = "storeId", required = false) Long storeId) {
        return Result.success(orderService.getMenuInfo(storeId));
    }

    @PostMapping("/order")
    public Result<OrderResponse> createOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateOrderCommand command) {
        if (command == null) throw new ServiceException(400, "请求不能为空");
        normalizeFulfillment(command);
        assertOrderIdentity(command.getUserId(), command.getGuestId());
        OrderResponse response = orderIdempotencyService.execute(idempotencyKey, command.getUserId(), command.getGuestId(), command, () -> {
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
                deliveryRequest.setAddressId(command.getDeliveryAddressId());
                deliveryRequest.setNote(command.getNote());
                DeliveryOrderResponse delivery = deliveryService.createDeliveryOrder(deliveryRequest);
                created.setDeliveryOrderId(delivery.getDeliveryOrderId());
            }
            // 下单即创建支付单，响应与支付单号一起缓存为幂等结果。
            PaymentResponse payment = paymentService.createForOrder(created.getOrderId());
            created.setPaymentNo(payment.getPaymentNo());
            return created;
        });
        return Result.success(response);
    }

    @PostMapping("/order/user/{id}/action")
    public Result<OrderResponse> updateUserOrder(@PathVariable Long id, @RequestParam String action,
                                                 @RequestParam Long userId) {
        AccessGuard.requireUser(userId);
        if (!"cancel".equalsIgnoreCase(action)) throw new ServiceException(400, "用户仅可取消待支付订单");
        if (!ownsOrder(id, userId, null)) throw new ServiceException(404, "订单不存在");
        return Result.success(orderService.updateOrderStatus(id, action, true));
    }

    @PostMapping("/order/guest/{id}/action")
    public Result<OrderResponse> updateGuestOrder(@PathVariable Long id, @RequestParam String action,
                                                  @RequestParam String guestId) {
        AccessGuard.requireGuest(guestId);
        if (!"cancel".equalsIgnoreCase(action)) throw new ServiceException(400, "游客仅可取消待支付订单");
        if (!ownsOrder(id, null, guestId)) throw new ServiceException(404, "订单不存在");
        return Result.success(orderService.updateOrderStatus(id, action, false));
    }

    @GetMapping("/orders/user/{userId}")
    public Result<List<Map<String, Object>>> getUserOrders(@PathVariable Long userId) {
        AccessGuard.requireUser(userId);
        return Result.success(orderService.getUserOrders(userId));
    }

    @GetMapping("/orders/guest/{guestId}")
    public Result<List<Map<String, Object>>> getGuestOrders(@PathVariable String guestId) {
        AccessGuard.requireGuest(guestId);
        return Result.success(orderService.getGuestOrders(guestId));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> getAllOrders(
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "status", required = false) String status) {
        if (storeId != null) {
            requireStoreOwner(storeId);
            return Result.success(orderService.getStoreOrders(storeId, status));
        }
        throw new ServiceException(400, "请指定店铺");
    }

    /** 商家操作订单状态（接单 accept / 开始制作 start / 完成制作 complete），校验订单归属店铺 */
    @PostMapping("/orders/{id}/action")
    public Result<OrderResponse> merchantOrderAction(@PathVariable Long id,
                                                     @RequestParam String action,
                                                     @RequestParam Long storeId) {
        requireStoreOwner(storeId);
        return Result.success(orderService.updateStoreOrderStatus(id, action, storeId));
    }

    @GetMapping("/member/{userId}/dashboard")
    public Result<Map<String, Object>> getMemberDashboard(@PathVariable Long userId) {
        AccessGuard.requireUser(userId);
        MemberDTO member = null;
        try {
            member = memberService.getMember(userId);
        } catch (ServiceException e) {
            // 会员不存在时按零消费处理
        }
        double spent = member != null && member.getTotalSpent() != null ? member.getTotalSpent() : 0;
        MemberLevelDTO levelDTO = memberService.getMemberLevel(userId);
        double nextThreshold = spent < MemberLevelDTO.VIP_THRESHOLD
                ? MemberLevelDTO.VIP_THRESHOLD
                : (spent < MemberLevelDTO.SVIP_THRESHOLD ? MemberLevelDTO.SVIP_THRESHOLD : MemberLevelDTO.SVIP_THRESHOLD);
        double progress = spent >= MemberLevelDTO.SVIP_THRESHOLD ? 100
                : (int) Math.min(100, spent / nextThreshold * 100);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nickname", member != null && member.getNickname() != null ? member.getNickname() : "会员");
        result.put("totalSpent", spent);
        result.put("totalSaved", orderService.getTotalSaved(userId));
        result.put("memberLevel", levelDTO.getLabel());
        result.put("points", member != null && member.getPoints() != null ? member.getPoints() : 0);
        result.put("pointsLevel", member != null && member.getPointsLevel() != null ? member.getPointsLevel() : "BRONZE");
        result.put("nextThreshold", nextThreshold);
        result.put("amountToNext", Math.max(0, nextThreshold - spent));
        result.put("progress", progress);
        result.put("coupons", List.of(
                couponInfo("FIKA8", "下午茶立减 ¥8", 48, 8, "下午茶品类满 ¥48 立减 ¥8"),
                couponInfo("SWEET12", "甜品满 ¥78 减 ¥12", 78, 12, "甜品品类满 ¥78 立减 ¥12"),
                couponInfo("BEAN15", "咖啡满 ¥88 减 ¥15", 88, 15, "咖啡品类满 ¥88 立减 ¥15")));
        return Result.success(result);
    }

    private Map<String, Object> couponInfo(String code, String name, double minimum, double discount, String description) {
        Map<String, Object> coupon = new LinkedHashMap<>();
        coupon.put("code", code);
        coupon.put("name", name);
        coupon.put("minimum", minimum);
        coupon.put("discount", discount);
        coupon.put("description", description);
        return coupon;
    }

    private void assertOrderIdentity(Long userId, String guestId) {
        if (userId != null && guestId != null && !guestId.isBlank()) {
            throw new ServiceException(400, "下单身份只能是用户或游客其中一种");
        }
        if (userId != null) AccessGuard.requireUser(userId);
        else if (guestId != null && !guestId.isBlank()) AccessGuard.requireGuest(guestId);
        else throw new ServiceException(400, "缺少下单身份");
    }

    private void normalizeFulfillment(CreateOrderCommand command) {
        String fulfillment = command.getFulfillmentType();
        fulfillment = fulfillment == null || fulfillment.isBlank()
                ? "PICKUP" : fulfillment.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PICKUP", "DINE_IN", "DELIVERY").contains(fulfillment)) {
            throw new ServiceException(400, "不支持的取餐方式");
        }
        command.setFulfillmentType(fulfillment);
        if ("DELIVERY".equals(fulfillment)) {
            if (command.getUserId() == null || command.getUserId() <= 0
                    || (command.getGuestId() != null && !command.getGuestId().isBlank())) {
                throw new ServiceException(400, "外卖配送需要先登录顾客账号");
            }
            if (command.getDeliveryAddressId() == null || command.getDeliveryAddressId() <= 0) {
                throw new ServiceException(400, "外卖配送请选择收货地址");
            }
        } else {
            command.setDeliveryAddressId(null);
        }
    }

    private boolean ownsOrder(Long orderId, Long userId, String guestId) {
        List<Map<String, Object>> orders = userId != null ? orderService.getUserOrders(userId) : orderService.getGuestOrders(guestId);
        return orders.stream().anyMatch(order -> Objects.equals(Long.valueOf(String.valueOf(order.get("id"))), orderId));
    }

    private void requireStoreOwner(Long storeId) {
        Long merchantId = AccessGuard.currentMerchantId();
        boolean ownsStore = storeService.listByMerchant(merchantId).stream()
                .anyMatch(store -> storeId.equals(store.getStoreId()));
        if (!ownsStore) throw new ServiceException(403, "无权操作其他商家的订单");
    }
}
