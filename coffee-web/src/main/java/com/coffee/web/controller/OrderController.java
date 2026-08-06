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

    public OrderController(OrderService orderService, MemberService memberService) {
        this.orderService = orderService;
        this.memberService = memberService;
    }

    @GetMapping("/menu")
    public Result<Map<String, Object>> getMenu(@RequestParam(value = "storeId", required = false) Long storeId) {
        return Result.success(orderService.getMenuInfo(storeId));
    }

    @PostMapping("/order")
    public Result<OrderResponse> createOrder(@RequestBody CreateOrderCommand command) {
        return Result.success(orderService.createOrder(command));
    }

    @PostMapping("/order/user/{id}/action")
    public Result<OrderResponse> updateUserOrder(@PathVariable Long id, @RequestParam String action) {
        return Result.success(orderService.updateOrderStatus(id, action, true));
    }

    @PostMapping("/order/guest/{id}/action")
    public Result<OrderResponse> updateGuestOrder(@PathVariable Long id, @RequestParam String action) {
        return Result.success(orderService.updateOrderStatus(id, action, false));
    }

    @GetMapping("/orders/user/{userId}")
    public Result<List<Map<String, Object>>> getUserOrders(@PathVariable Long userId) {
        return Result.success(orderService.getUserOrders(userId));
    }

    @GetMapping("/orders/guest/{guestId}")
    public Result<List<Map<String, Object>>> getGuestOrders(@PathVariable String guestId) {
        return Result.success(orderService.getGuestOrders(guestId));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> getAllOrders(
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "status", required = false) String status) {
        if (storeId != null) {
            return Result.success(orderService.getStoreOrders(storeId, status));
        }
        return Result.success(orderService.getAllOrders());
    }

    /** 商家操作订单状态（接单 start / 完成 complete / 取消 cancel），校验订单归属店铺 */
    @PostMapping("/orders/{id}/action")
    public Result<OrderResponse> merchantOrderAction(@PathVariable Long id,
                                                     @RequestParam String action,
                                                     @RequestParam Long storeId) {
        return Result.success(orderService.updateStoreOrderStatus(id, action, storeId));
    }

    @GetMapping("/member/{userId}/dashboard")
    public Result<Map<String, Object>> getMemberDashboard(@PathVariable Long userId) {
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
}
