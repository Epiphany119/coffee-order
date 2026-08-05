package com.coffee.module.order.api.dto;

import lombok.Data;
import java.util.List;

/**
 * 创建订单命令
 */
@Data
public class CreateOrderCommand {
    private Long userId;
    private String guestId;
    private String productCode;
    private String size;
    private List<String> condiments;
    private List<CartItemCommand> items;
    private String couponCode;

    public boolean isBatch() {
        return items != null && !items.isEmpty();
    }
}
