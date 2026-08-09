package com.coffee.order.controller;

import com.coffee.module.order.api.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 服务边界探针，供 Gateway/Nacos 部署验收。 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderHealthController {
    private final OrderService orderService;
    public InternalOrderHealthController(OrderService orderService) { this.orderService = orderService; }
    @GetMapping("/capability") public Map<String, Object> capability() {
        return Map.of("service", "coffee-order-service", "orderApi", orderService.getClass().getSimpleName());
    }
}
