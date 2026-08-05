package com.coffee.order.domain.product.repository;

import com.coffee.order.domain.product.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * 产品仓储接口
 */
public interface ProductRepository {
    Optional<Product> findByCode(String code);
    List<Product> findAllAvailable();
    List<Product> findByCategory(String categoryCode);
}
