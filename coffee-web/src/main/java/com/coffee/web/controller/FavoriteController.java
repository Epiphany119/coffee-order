package com.coffee.web.controller;

import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.web.security.AccessGuard;
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
        assertOwner(userId, guestId);
        return favoriteService.getFavorites(userId, guestId);
    }

    /** 添加收藏：body 中 userId/guestId 至少一个 */
    @PostMapping
    public Map<String, Object> addFavorite(@RequestBody Map<String, Object> body) {
        if (body == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        Long userId = body.get("userId") == null ? null : Long.valueOf(body.get("userId").toString());
        String guestId = body.get("guestId") == null ? null : body.get("guestId").toString();
        assertOwner(userId, guestId);
        Object productCodeValue = body.get("productCode");
        if (productCodeValue == null || productCodeValue.toString().isBlank()) {
            throw new com.coffee.common.core.exception.ServiceException(400, "商品编码不能为空");
        }
        String productCode = productCodeValue.toString().trim();
        favoriteService.addFavorite(userId, guestId, productCode);
        return Map.of("success", true, "message", "已添加到收藏");
    }

    /** 取消收藏：DELETE /api/favorites?userId=1&productCode=xx 或 ?guestId=... */
    @DeleteMapping
    public Map<String, Object> removeFavorite(@RequestParam(value = "userId", required = false) Long userId,
                                              @RequestParam(value = "guestId", required = false) String guestId,
                                              @RequestParam("productCode") String productCode) {
        assertOwner(userId, guestId);
        favoriteService.removeFavorite(userId, guestId, productCode);
        return Map.of("success", true, "message", "已取消收藏");
    }

    /** 游客登录后合并收藏到用户账号 */
    @PostMapping("/merge")
    public Map<String, Object> merge(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("userId") == null || body.get("guestId") == null
                || body.get("guestId").toString().isBlank()) {
            throw new com.coffee.common.core.exception.ServiceException(400, "缺少合并身份");
        }
        Long userId = Long.valueOf(body.get("userId").toString());
        String guestId = body.get("guestId").toString().trim();
        AccessGuard.requireUser(userId);
        favoriteService.mergeGuestToUser(userId, guestId);
        return Map.of("success", true, "message", "收藏已合并");
    }

    private void assertOwner(Long userId, String guestId) {
        if (userId != null && guestId != null && !guestId.isBlank()) {
            throw new com.coffee.common.core.exception.ServiceException(400, "身份参数不能同时传入");
        }
        if (userId != null) AccessGuard.requireUser(userId);
        else if (guestId != null && !guestId.isBlank()) AccessGuard.requireGuest(guestId);
        else throw new com.coffee.common.core.exception.ServiceException(400, "缺少用户身份");
    }
}
