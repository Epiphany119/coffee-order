package com.coffee.module.menu.api;

import com.coffee.module.menu.api.dto.MenuItemDTO;

import java.util.List;

/**
 * 收藏服务 API
 * <p>
 * 支持双身份：登录用户（userId）与游客（guestId），两者至少传一个非空。
 */
public interface FavoriteService {

    /**
     * 获取收藏的产品列表（按 userId 或 guestId 隔离）
     */
    List<MenuItemDTO> getFavorites(Long userId, String guestId);

    /**
     * 添加收藏（userId 或 guestId 至少一个非空，幂等查重）
     */
    void addFavorite(Long userId, String guestId, String productCode);

    /**
     * 取消收藏
     */
    void removeFavorite(Long userId, String guestId, String productCode);

    /**
     * 游客登录后合并收藏：把 guestId 的收藏并入 userId（查重），合并后删除游客收藏记录
     */
    void mergeGuestToUser(Long userId, String guestId);
}
