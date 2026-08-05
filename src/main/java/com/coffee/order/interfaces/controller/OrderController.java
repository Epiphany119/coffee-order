package com.coffee.order.interfaces.controller;

import com.coffee.order.application.dto.CreateOrderCommand;
import com.coffee.order.application.dto.OrderResponseDTO;
import com.coffee.order.application.service.OrderApplicationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单接口层 - DDD 架构的控制器
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @GetMapping("/menu")
    public Map<String, Object> getMenu() {
        return orderApplicationService.getMenuInfo();
    }

    @PostMapping("/order")
    public OrderResponseDTO createOrder(@RequestBody CreateOrderCommand command) {
        return orderApplicationService.createOrder(command);
    }

    @PostMapping("/order/user/{id}/action")
    public OrderResponseDTO updateUserOrder(@PathVariable Long id, @RequestParam String action) {
        return orderApplicationService.updateOrderStatus(id, action, true);
    }

    @PostMapping("/order/guest/{id}/action")
    public OrderResponseDTO updateGuestOrder(@PathVariable Long id, @RequestParam String action) {
        return orderApplicationService.updateOrderStatus(id, action, false);
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
}
