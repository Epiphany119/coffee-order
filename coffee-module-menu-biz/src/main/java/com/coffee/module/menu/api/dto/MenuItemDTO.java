package com.coffee.module.menu.api.dto;

import lombok.Data;

/**
 * 商品 DTO（菜单项）
 */
@Data
public class MenuItemDTO {
    private Long id;
    /** 所属店铺 */
    private Long storeId;
    private String code;
    private String name;
    /** 分类外键（menu_category.id，与 categoryCode 保持一致） */
    private Long categoryId;
    private String categoryCode;
    /** 基础价（规格价缺失时的兜底价） */
    private Double basePrice;
    /** 规格定价：小份/基础款 */
    private Double priceSmall;
    /** 规格定价：中份/中等款 */
    private Double priceMedium;
    /** 规格定价：大份/加大款 */
    private Double priceLarge;
    /** 定制规格单位：coffee/tea/ice → ml，dessert/food → g（定制规格输入用） */
    private String customUnit;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;
}
