package com.coffee.module.product.api.impl;

import com.coffee.module.product.api.ProductFeignApi;
import com.coffee.module.product.api.ProductService;
import com.coffee.module.product.api.dto.ProductDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品服务 Feign 实现
 */
@Service
public class ProductFeignApiImpl implements ProductFeignApi {

    private final ProductService productService;

    public ProductFeignApiImpl(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ProductDTO getProductByCode(String code) {
        return productService.getProductByCode(code);
    }

    @Override
    public List<ProductDTO> getAllProducts() {
        return productService.getAllProducts();
    }

    @Override
    public double calculatePrice(String productCode, String size, List<String> condiments) {
        return productService.calculatePrice(productCode, size, condiments);
    }

    @Override
    public String getDisplayName(String productCode, String size, List<String> condiments) {
        return productService.getDisplayName(productCode, size, condiments);
    }
}
