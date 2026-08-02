package com.coffee.order.controller;

import com.coffee.order.entity.Product;
import com.coffee.order.entity.UserFavorite;
import com.coffee.order.repository.CoffeeBeverageRepository;
import com.coffee.order.repository.ProductRepository;
import com.coffee.order.repository.UserFavoriteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "*")
public class FavoriteController {

    private final UserFavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;

    public FavoriteController(UserFavoriteRepository favoriteRepository, ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getFavorites(@PathVariable Long userId) {
        List<UserFavorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<String> codes = favorites.stream()
                .map(UserFavorite::getProductCode)
                .collect(Collectors.toList());
        List<Product> products = productRepository.findByCodeIn(codes);
        return ResponseEntity.ok(products);
    }

    @PostMapping
    public ResponseEntity<?> addFavorite(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String productCode = body.get("productCode").toString();

        if (favoriteRepository.existsByUserIdAndProductCode(userId, productCode)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "已在收藏中"));
        }

        UserFavorite favorite = new UserFavorite(userId, productCode);
        favoriteRepository.save(favorite);
        return ResponseEntity.ok(Map.of("success", true, "message", "已添加到收藏"));
    }

    @DeleteMapping("/{userId}/{productCode}")
    public ResponseEntity<?> removeFavorite(@PathVariable Long userId, @PathVariable String productCode) {
        favoriteRepository.deleteByUserIdAndProductCode(userId, productCode);
        return ResponseEntity.ok(Map.of("success", true, "message", "已取消收藏"));
    }
}
