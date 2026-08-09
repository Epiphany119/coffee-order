package com.coffee.module.inventory.api;

/**
 * 库存服务契约。订单只依赖此接口，部署为独立服务后可由 HTTP / RPC 适配器替换实现。
 */
public interface InventoryService {
    void reserve(Long storeId, Long productId, int quantity);

    void release(Long storeId, Long productId, int quantity);
}
