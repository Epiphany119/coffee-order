package com.coffee.module.product.api;

import com.coffee.module.product.api.dto.ProductDTO;

import java.util.List;

/**
 * 收藏服务 API
 */
public interface FavoriteService {

    /**
     * 获取用户收藏的产品列表
     */
    List<ProductDTO> getFavorites(Long userId);

    /**
     * 添加收藏
     */
    void addFavorite(Long userId, String productCode);

    /**
     * 取消收藏
     */
    void removeFavorite(Long userId, String productCode);
}
