package com.coffee.order.repository;

import com.coffee.order.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserFavorite> findByUserIdAndProductCode(Long userId, String productCode);

    boolean existsByUserIdAndProductCode(Long userId, String productCode);

    void deleteByUserIdAndProductCode(Long userId, String productCode);
}
