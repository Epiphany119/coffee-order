package com.coffee.module.product.api;

import com.coffee.module.product.api.dto.ProductDTO;

import java.util.List;

/**
 * 产品服务 Feign API
 */
public interface ProductFeignApi {

    ProductDTO getProductByCode(String code);

    List<ProductDTO> getAllProducts();

    double calculatePrice(String productCode, String size, List<String> condiments);

    String getDisplayName(String productCode, String size, List<String> condiments);
}
