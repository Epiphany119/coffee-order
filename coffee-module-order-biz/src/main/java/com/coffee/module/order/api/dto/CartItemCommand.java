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
    private List<String> condiments;
    private Integer quantity = 1;
}
