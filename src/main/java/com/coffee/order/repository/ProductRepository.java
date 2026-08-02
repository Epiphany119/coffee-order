package com.coffee.order.repository;

import com.coffee.order.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);
    List<Product> findByCodeIn(List<String> codes);
    List<Product> findByCategoryCodeOrderByIdAsc(String categoryCode);
    List<Product> findByAvailableTrueOrderByCategoryCodeAscIdAsc();
}
