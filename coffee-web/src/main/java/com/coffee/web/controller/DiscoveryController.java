package com.coffee.web.controller;

import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 用户侧发现页能力：服务端检索与基于收藏品类的轻量推荐。 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private final MenuService menuService;
    private final FavoriteService favoriteService;

    public DiscoveryController(MenuService menuService, FavoriteService favoriteService) {
        this.menuService = menuService;
        this.favoriteService = favoriteService;
    }

    @GetMapping("/search")
    public List<MenuItemDTO> search(@RequestParam Long storeId, @RequestParam String keyword,
                                    @RequestParam(defaultValue = "12") int limit) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return List.of();
        return menuService.getAllProducts(storeId).stream()
                .filter(item -> matches(item, normalized))
                .limit(Math.max(1, Math.min(limit, 30)))
                .toList();
    }

    @GetMapping("/recommendations")
    public List<MenuItemDTO> recommendations(@RequestParam Long storeId,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String guestId,
                                              @RequestParam(defaultValue = "8") int limit) {
        boolean hasUser = userId != null;
        boolean hasGuest = guestId != null && !guestId.isBlank();
        if (hasUser == hasGuest) {
            throw new ServiceException(400, "请提供一种有效的用户或游客身份");
        }
        if (hasUser) AccessGuard.requireUser(userId);
        else AccessGuard.requireGuest(guestId);
        List<MenuItemDTO> favorites = favoriteService.getFavorites(userId, guestId);
        Set<String> favoriteCategories = favorites.stream()
                .map(MenuItemDTO::getCategoryCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (favoriteCategories.isEmpty()) favoriteCategories.add("coffee");
        int safeLimit = Math.max(1, Math.min(limit, 12));
        return menuService.getAllProducts(storeId).stream()
                .sorted(Comparator.comparing((MenuItemDTO item) -> !favoriteCategories.contains(item.getCategoryCode()))
                        .thenComparing(MenuItemDTO::getId))
                .limit(safeLimit)
                .toList();
    }

    private boolean matches(MenuItemDTO item, String keyword) {
        String text = (safe(item.getName()) + ' ' + safe(item.getDescription()) + ' ' + safe(item.getCategoryCode()))
                .toLowerCase(Locale.ROOT);
        return text.contains(keyword);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
