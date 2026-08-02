package com.coffee.order.repository;

import com.coffee.order.entity.CoffeeBeverage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoffeeBeverageRepository extends JpaRepository<CoffeeBeverage, Long> {
    Optional<CoffeeBeverage> findByType(String type);
}
