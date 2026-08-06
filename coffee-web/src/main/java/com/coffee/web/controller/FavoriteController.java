package com.coffee.web.controller;

import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏控制器（userId 登录用户 / guestId 游客，数据按身份隔离入库）
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 查询收藏：GET /api/favorites?userId=1 或 ?guestId=g-xxx */
    @GetMapping
    public List<MenuItemDTO> getFavorites(@RequestParam(value = "userId", required = false) Long userId,
                                         @RequestParam(value = "guestId", required = false) String guestId) {
        return favoriteService.getFavorites(userId, guestId);
    }

    /** 添加收藏：body 中 userId/guestId 至少一个 */
    @PostMapping
    public Map<String, Object> addFavorite(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") == null ? null : Long.valueOf(body.get("userId").toString());
        String guestId = body.get("guestId") == null ? null : body.get("guestId").toString();
        String productCode = body.get("productCode").toString();
        favoriteService.addFavorite(userId, guestId, productCode);
        return Map.of("success", true, "message", "已添加到收藏");
    }

    /** 取消收藏：DELETE /api/favorites?userId=1&productCode=xx 或 ?guestId=... */
    @DeleteMapping
    public Map<String, Object> removeFavorite(@RequestParam(value = "userId", required = false) Long userId,
                                              @RequestParam(value = "guestId", required = false) String guestId,
                                              @RequestParam("productCode") String productCode) {
        favoriteService.removeFavorite(userId, guestId, productCode);
        return Map.of("success", true, "message", "已取消收藏");
    }

    /** 游客登录后合并收藏到用户账号 */
    @PostMapping("/merge")
    public Map<String, Object> merge(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String guestId = body.get("guestId").toString();
        favoriteService.mergeGuestToUser(userId, guestId);
        return Map.of("success", true, "message", "收藏已合并");
    }
}
