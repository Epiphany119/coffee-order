package com.coffee.order.controller;

import com.coffee.order.dto.OrderRequest;
import com.coffee.order.dto.OrderResponse;
import com.coffee.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/menu")
    public Map<String, Object> getMenu() {
        return orderService.getMenuInfo();
    }

    @PostMapping("/order")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }

    @PostMapping("/order/user/{id}/action")
    public OrderResponse updateUserOrder(@PathVariable Long id, @RequestParam String action) {
        return orderService.updateOrderStatus(id, action, true);
    }

    @PostMapping("/order/guest/{id}/action")
    public OrderResponse updateGuestOrder(@PathVariable Long id, @RequestParam String action) {
        return orderService.updateOrderStatus(id, action, false);
    }

    @GetMapping("/orders/user/{userId}")
    public List<Map<String, Object>> getUserOrders(@PathVariable Long userId) {
        return orderService.getUserOrders(userId);
    }

    @GetMapping("/orders/guest/{guestId}")
    public List<Map<String, Object>> getGuestOrders(@PathVariable String guestId) {
        return orderService.getGuestOrders(guestId);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/member/{userId}/dashboard")
    public Map<String, Object> getMemberDashboard(@PathVariable Long userId) {
        return orderService.getMemberDashboard(userId);
    }
}
