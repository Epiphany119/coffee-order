package com.coffee.module.product.api.dto;

import lombok.Data;

/**
 * 产品 DTO
 */
@Data
public class ProductDTO {
    private Long id;
    private String code;
    private String name;
    private String categoryCode;
    private Double basePrice;
    private String description;
    private String imageUrl;
    private String temperature;
    private Boolean available;
}
