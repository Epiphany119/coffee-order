package com.coffee.module.product.biz.domain.repository;

import com.coffee.module.product.biz.domain.Product;
import java.util.List;

/**
 * 产品仓储接口
 */
public interface ProductRepository {
    Product findByCode(String code);
    List<Product> findAllAvailable();
    List<Product> findByCategory(String categoryCode);
    List<Product> findByCodeIn(List<String> codes);
}
