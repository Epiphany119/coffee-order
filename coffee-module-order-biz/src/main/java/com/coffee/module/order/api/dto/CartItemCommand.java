package com.coffee.module.order.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 购物车项命令
 */
@Data
public class CartItemCommand {
    private String productCode;
    private String size;
    /** 定制尺寸输入（如 "300"），size=CUSTOM 时有效 */
    private String customSize;
    private List<String> condiments;
    private Integer quantity = 1;
}
