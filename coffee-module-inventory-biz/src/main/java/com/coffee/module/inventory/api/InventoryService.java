package com.coffee.module.inventory.api;

/**
 * 库存服务契约。订单只依赖此接口，部署为独立服务后可由 HTTP / RPC 适配器替换实现。
 */
public interface InventoryService {
    /** 只读库存检查；不创建库存记录、不锁定库存。 */
    boolean hasAvailable(Long storeId, Long productId, int quantity);

    /** 返回库存只读快照；尚无库存记录时应返回与首次售卖规则一致的初始可用量。 */
    int availableQuantity(Long storeId, Long productId);

    void reserve(Long storeId, Long productId, int quantity);

    void release(Long storeId, Long productId, int quantity);
}
