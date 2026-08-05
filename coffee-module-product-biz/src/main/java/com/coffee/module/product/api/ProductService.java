package com.coffee.module.product.api;

import com.coffee.module.product.api.dto.ProductDTO;

import java.util.List;

/**
 * 产品服务 API
 */
public interface ProductService {

    /**
     * 根据编码获取产品
     */
    ProductDTO getProductByCode(String code);

    /**
     * 获取所有可用产品
     */
    List<ProductDTO> getAllProducts();

    /**
     * 计算产品总价
     */
    double calculatePrice(String productCode, String size, List<String> condiments);

    /**
     * 获取产品展示名称
     */
    String getDisplayName(String productCode, String size, List<String> condiments);
}
