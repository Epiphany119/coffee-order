package com.coffee.module.menu.biz.domain;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 商品领域对象（菜单项）
 */
@Data
public class MenuItem {
    private Long id;
    /** 所属店铺（按店隔离菜单：每店一份商品） */
    private Long storeId;
    private String code;
    private String name;
    /** 分类外键（menu_category.id，与 categoryCode 保持一致） */
    private Long categoryId;
    private String categoryCode;
    /** 基础价（规格价缺失时的兜底价） */
    private BigDecimal basePrice;
    /** 规格定价：小份/基础款 */
    private Double priceSmall;
    /** 规格定价：中份/中等款 */
    private Double priceMedium;
    /** 规格定价：大份/加大款 */
    private Double priceLarge;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;

    public double getBasePriceAsDouble() {
        return basePrice != null ? basePrice.doubleValue() : 0.0;
    }

    /** 取指定规格的定价：规格价优先，缺失回退 basePrice（与后端口径一致，不再有前端/后端两套算法） */
    public double getSizePrice(String size) {
        Double price = switch (size == null ? "MEDIUM" : size) {
            case "SMALL" -> priceSmall;
            case "LARGE" -> priceLarge;
            default -> priceMedium;
        };
        return price != null ? price : getBasePriceAsDouble();
    }
}
