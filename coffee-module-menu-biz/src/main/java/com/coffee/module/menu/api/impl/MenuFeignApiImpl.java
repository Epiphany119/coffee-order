package com.coffee.module.menu.api.impl;

import com.coffee.module.menu.api.MenuFeignApi;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品服务 Feign 实现
 */
@Service
public class MenuFeignApiImpl implements MenuFeignApi {

    private final MenuService productService;

    public MenuFeignApiImpl(MenuService productService) {
        this.productService = productService;
    }

    @Override
    public MenuItemDTO getProductByCode(Long storeId, String code) {
        return productService.getProductByCode(storeId, code);
    }

    @Override
    public List<MenuItemDTO> getAllProducts(Long storeId) {
        return productService.getAllProducts(storeId);
    }

    @Override
    public double calculatePrice(Long storeId, String productCode, String size, String customSize, List<String> condiments) {
        return productService.calculatePrice(storeId, productCode, size, customSize, condiments);
    }

    @Override
    public String getDisplayName(Long storeId, String productCode, String size, String customSize, List<String> condiments) {
        return productService.getDisplayName(storeId, productCode, size, customSize, condiments);
    }
}
