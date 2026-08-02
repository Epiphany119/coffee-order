package com.coffee.order.repository;

import com.coffee.order.entity.CoffeeUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoffeeUserRepository extends JpaRepository<CoffeeUser, Long> {
    Optional<CoffeeUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
