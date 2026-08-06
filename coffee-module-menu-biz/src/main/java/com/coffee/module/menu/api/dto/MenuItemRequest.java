package com.coffee.module.menu.api.dto;

import lombok.Data;

/**
 * 商家菜单管理请求（新增/更新商品）
 */
@Data
public class MenuItemRequest {
    /** 商品编码（新增时必填，店内唯一） */
    private String code;
    /** 商品名称 */
    private String name;
    /** 分类外键（menu_category.id；可不传，后端按 categoryCode 反查回填） */
    private Long categoryId;
    /** 分类编码（coffee/tea/dessert/food/ice 或商家自定义类目编码） */
    private String categoryCode;
    /** 基础价格（规格价缺失时的兜底价） */
    private Double basePrice;
    /** 规格定价：小份/基础款 */
    private Double priceSmall;
    /** 规格定价：中份/中等款 */
    private Double priceMedium;
    /** 规格定价：大份/加大款 */
    private Double priceLarge;
    private String description;
    private String imageUrl;
    /** 冷热：HOT/BOTH/COLD/ROOM */
    private String temperature;
    /** 上架状态（更新时可用） */
    private Boolean available;
}
