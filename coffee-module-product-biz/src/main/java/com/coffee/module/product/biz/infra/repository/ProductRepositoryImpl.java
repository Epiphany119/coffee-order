package com.coffee.module.product.biz.infra.repository;

import com.coffee.module.product.biz.domain.Product;
import com.coffee.module.product.biz.domain.repository.ProductRepository;
import com.coffee.module.product.biz.infra.persistence.ProductMapper;
import com.coffee.module.product.biz.infra.persistence.ProductPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品仓储实现
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    public ProductRepositoryImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public Product findByCode(String code) {
        ProductPO po = productMapper.selectByCode(code);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public List<Product> findAllAvailable() {
        return productMapper.selectAllAvailable().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByCategory(String categoryCode) {
        return productMapper.selectByCategory(categoryCode).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findByCodeIn(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return productMapper.selectByCodes(codes).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Product toDomain(ProductPO po) {
        Product product = new Product();
        product.setId(po.getId());
        product.setCode(po.getCode());
        product.setName(po.getName());
        product.setCategoryCode(po.getCategoryCode());
        product.setBasePrice(po.getBasePrice());
        product.setDescription(po.getDescription());
        product.setImageUrl(po.getImageUrl());
        product.setTemperature(po.getTemperature());
        product.setAvailable(po.getAvailable());
        return product;
    }
}
