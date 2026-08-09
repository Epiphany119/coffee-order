package com.coffee.inventory.controller;

import com.coffee.module.inventory.api.InventoryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内部库存命令入口；网关不暴露该路径，供订单服务经服务发现调用。 */
@RestController
@RequestMapping("/internal/inventories")
public class InternalInventoryController {
    private final InventoryService inventoryService;

    public InternalInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<Void> reserve(@RequestBody InventoryCommand command) {
        inventoryService.reserve(command.storeId(), command.productId(), command.quantity());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestBody InventoryCommand command) {
        inventoryService.release(command.storeId(), command.productId(), command.quantity());
        return ResponseEntity.noContent().build();
    }

    public record InventoryCommand(@NotNull Long storeId, @NotNull Long productId, @Min(1) int quantity) { }
}
