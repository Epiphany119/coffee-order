package com.coffee.web.controller;

import com.coffee.module.product.api.FavoriteService;
import com.coffee.module.product.api.dto.ProductDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏控制器
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/{userId}")
    public List<ProductDTO> getFavorites(@PathVariable Long userId) {
        return favoriteService.getFavorites(userId);
    }

    @PostMapping
    public Map<String, Object> addFavorite(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String productCode = body.get("productCode").toString();
        favoriteService.addFavorite(userId, productCode);
        return Map.of("success", true, "message", "已添加到收藏");
    }

    @DeleteMapping("/{userId}/{productCode}")
    public Map<String, Object> removeFavorite(@PathVariable Long userId, @PathVariable String productCode) {
        favoriteService.removeFavorite(userId, productCode);
        return Map.of("success", true, "message", "已取消收藏");
    }
}
