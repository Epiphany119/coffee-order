package com.coffee.module.product.biz.domain;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 产品领域对象
 */
@Data
public class Product {
    private Long id;
    private String code;
    private String name;
    private String categoryCode;
    private BigDecimal basePrice;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;

    public double getBasePriceAsDouble() {
        return basePrice != null ? basePrice.doubleValue() : 0.0;
    }
}
