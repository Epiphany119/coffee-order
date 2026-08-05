package com.coffee.order.controller;

import com.coffee.order.application.dto.CreateOrderCommand;
import com.coffee.order.application.dto.CartItemCommand;
import com.coffee.order.application.dto.OrderResponseDTO;
import com.coffee.order.application.service.OrderApplicationService;
import com.coffee.order.domain.member.service.MemberDomainService;
import com.coffee.order.domain.member.valueobject.MemberLevel;
import com.coffee.order.dto.OrderRequest;
import com.coffee.order.dto.OrderResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OrderController {
    private final OrderApplicationService orderApplicationService;
    private final MemberDomainService memberDomainService;

    public OrderController(OrderApplicationService orderApplicationService,
                         MemberDomainService memberDomainService) {
        this.orderApplicationService = orderApplicationService;
        this.memberDomainService = memberDomainService;
    }

    @GetMapping("/menu")
    public Map<String, Object> getMenu() {
        return orderApplicationService.getMenuInfo();
    }

    @PostMapping("/order")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        CreateOrderCommand command = toCommand(request);
        OrderResponseDTO dto = orderApplicationService.createOrder(command);
        return toResponse(dto);
    }

    @PostMapping("/order/user/{id}/action")
    public OrderResponse updateUserOrder(@PathVariable Long id, @RequestParam String action) {
        OrderResponseDTO dto = orderApplicationService.updateOrderStatus(id, action, true);
        return toSimpleResponse(dto);
    }

    @PostMapping("/order/guest/{id}/action")
    public OrderResponse updateGuestOrder(@PathVariable Long id, @RequestParam String action) {
        OrderResponseDTO dto = orderApplicationService.updateOrderStatus(id, action, false);
        return toSimpleResponse(dto);
    }

    @GetMapping("/orders/user/{userId}")
    public List<Map<String, Object>> getUserOrders(@PathVariable Long userId) {
        return orderApplicationService.getUserOrders(userId);
    }

    @GetMapping("/orders/guest/{guestId}")
    public List<Map<String, Object>> getGuestOrders(@PathVariable String guestId) {
        return orderApplicationService.getGuestOrders(guestId);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> getAllOrders() {
        return orderApplicationService.getAllOrders();
    }

    @GetMapping("/member/{userId}/dashboard")
    public Map<String, Object> getMemberDashboard(@PathVariable Long userId) {
        double spent = memberDomainService.getTotalSpent(userId);
        MemberLevel level = MemberLevel.fromTotalSpent(spent);
        double nextThreshold = spent < MemberLevel.VIP_THRESHOLD
                ? MemberLevel.VIP_THRESHOLD
                : (spent < MemberLevel.SVIP_THRESHOLD ? MemberLevel.SVIP_THRESHOLD : MemberLevel.SVIP_THRESHOLD);
        double progress = spent >= MemberLevel.SVIP_THRESHOLD ? 100
                : Math.round(Math.min(100, spent / nextThreshold * 100));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSpent", spent);
        result.put("memberLevel", level.label());
        result.put("nextThreshold", nextThreshold);
        result.put("amountToNext", Math.max(0, Math.round(nextThreshold - spent)));
        result.put("progress", progress);
        result.put("coupons", List.of(
                couponInfo("FIKA8", "下午茶立减 ¥8", 48, 8, "全品类可用 · 会员权益券"),
                couponInfo("SWEET12", "甜品满 ¥78 减 ¥12", 78, 12, "咖啡、轻食和甜点一起享"),
                couponInfo("BEAN15", "咖啡满 ¥88 减 ¥15", 88, 15, "适合和朋友一起点")));
        return result;
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

    private CreateOrderCommand toCommand(OrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand();
        command.setUserId(request.getUserId());
        command.setGuestId(request.getGuestId());
        command.setProductCode(request.getProductCode());
        command.setSize(request.getSize());
        command.setCondiments(request.getCondiments());
        command.setCouponCode(request.getCouponCode());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<CartItemCommand> items = request.getItems().stream()
                    .map(this::toCartItemCommand)
                    .toList();
            command.setItems(items);
        }
        return command;
    }

    private CartItemCommand toCartItemCommand(com.coffee.order.dto.CartItemRequest request) {
        CartItemCommand command = new CartItemCommand();
        command.setProductCode(request.getProductCode());
        command.setSize(request.getSize());
        command.setCondiments(request.getCondiments());
        command.setQuantity(request.getQuantity());
        return command;
    }

    private OrderResponse toResponse(OrderResponseDTO dto) {
        OrderResponse response = new OrderResponse(
                dto.getOrderId(), dto.getOrderName(),
                dto.getOriginalPrice(), dto.getFinalPrice(),
                dto.getPricingStrategy(), dto.getStatus(),
                dto.getMessage(), dto.getTotalSpent(), dto.getMemberLevel(),
                dto.getCategoryCode());
        response.setTotalCups(dto.getTotalCups());
        response.setMemberDiscount(dto.getMemberDiscount());
        response.setCouponDiscount(dto.getCouponDiscount());
        response.setCouponName(dto.getCouponName());
        response.setEarnedPoints(dto.getEarnedPoints());
        response.setEstimatedReadyTime(dto.getEstimatedReadyTime());
        return response;
    }

    private OrderResponse toSimpleResponse(OrderResponseDTO dto) {
        return new OrderResponse(dto.getOrderId(), dto.getOrderName(),
                0, 0, "", dto.getStatus(), dto.getMessage(), 0, "");
    }
}
