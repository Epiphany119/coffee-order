package com.coffee.order.infrastructure.persistence.repository;

import com.coffee.order.domain.product.entity.Product;
import com.coffee.order.domain.product.repository.ProductRepository;
import com.coffee.order.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA 产品仓储实现
 */
@Repository
public class JpaProductRepository implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public JpaProductRepository(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Product> findByCode(String code) {
        return jpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public List<Product> findAllAvailable() {
        return jpaRepository.findByAvailableTrueOrderByCategoryCodeAscIdAsc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByCategory(String categoryCode) {
        return jpaRepository.findByCategoryCode(categoryCode)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private Product toDomain(ProductEntity entity) {
        Product product = new Product();
        product.setId(entity.getId());
        product.setCode(entity.getCode());
        product.setName(entity.getName());
        product.setCategoryCode(entity.getCategoryCode());
        product.setBasePrice(entity.getBasePrice());
        product.setDescription(entity.getDescription());
        product.setImageUrl(entity.getImageUrl());
        product.setTemperature(entity.getTemperature());
        product.setAvailable(entity.getAvailable());
        return product;
    }

    public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
        Optional<ProductEntity> findByCode(String code);
        List<ProductEntity> findByAvailableTrueOrderByCategoryCodeAscIdAsc();
        List<ProductEntity> findByCategoryCode(String categoryCode);
    }
}
