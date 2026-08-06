package com.coffee.module.menu.api;

import com.coffee.module.menu.api.dto.MenuItemDTO;

import java.util.List;

/**
 * 产品服务 Feign API
 */
public interface MenuFeignApi {

    MenuItemDTO getProductByCode(Long storeId, String code);

    List<MenuItemDTO> getAllProducts(Long storeId);

    double calculatePrice(Long storeId, String productCode, String size, String customSize, List<String> condiments);

    String getDisplayName(Long storeId, String productCode, String size, String customSize, List<String> condiments);
}
