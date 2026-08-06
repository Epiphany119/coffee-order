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
    /** 下单店铺（用户端切换店铺后绑定当前店铺） */
    private Long storeId;
    /** 取餐方式（PICKUP 到店自取 / DINE_IN 店内用餐） */
    private String fulfillmentType;
    /** 订单备注 */
    private String note;
    private String productCode;
    private String size;
    /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
    private String customSize;
    private List<String> condiments;
    private List<CartItemCommand> items;
    private String couponCode;

    public boolean isBatch() {
        return items != null && !items.isEmpty();
    }
}
