package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 点单 Agent 的确定性价格工具：基于当前门店、当前规格的真实价格排序。
 * 这是业务工具而不是模型猜测，确保“最贵/便宜/中档/有排面”可以稳定复现。
 */
@Component
class MenuPriceSelectionTool {
    private final MenuService menuService;

    MenuPriceSelectionTool(MenuService menuService) { this.menuService = menuService; }

    List<MenuItemDTO> sort(Long storeId, List<MenuItemDTO> products, CustomerOrderIntentParser.PricePreference preference) {
        if (preference == CustomerOrderIntentParser.PricePreference.NONE) return products;
        Comparator<MenuItemDTO> identity = Comparator.comparing(MenuItemDTO::getId,
                        Comparator.nullsLast(Long::compareTo))
                .thenComparing(product -> safe(product.getCode()))
                .thenComparing(product -> safe(product.getName()));
        List<MenuItemDTO> sorted = products.stream()
                .sorted(Comparator.comparingDouble((MenuItemDTO product) -> price(storeId, product))
                        .thenComparing(identity))
                .toList();
        if (preference == CustomerOrderIntentParser.PricePreference.CHEAP) return sorted;
        if (preference == CustomerOrderIntentParser.PricePreference.MOST_EXPENSIVE) return sorted.stream()
                .sorted(Comparator.comparingDouble((MenuItemDTO product) -> price(storeId, product))
                        .reversed().thenComparing(identity)).toList();
        if (preference == CustomerOrderIntentParser.PricePreference.MID) {
            double median = price(storeId, sorted.get(sorted.size() / 2));
            return sorted.stream().sorted(Comparator.<MenuItemDTO>comparingDouble(product -> Math.abs(price(storeId, product) - median))
                    .thenComparingDouble(product -> price(storeId, product))
                    .thenComparing(identity)).toList();
        }
        // “有排面/请客”取价格上三分位优先，避免把最低价商品包装成精品。
        int boundary = Math.max(0, (int) Math.floor(sorted.size() * 2d / 3d));
        return sorted.stream().sorted(Comparator.<MenuItemDTO>comparingInt(product -> sorted.indexOf(product) < boundary ? 1 : 0)
                .thenComparingDouble(product -> -price(storeId, product))
                .thenComparing(identity)).toList();
    }

    private double price(Long storeId, MenuItemDTO product) {
        return menuService.calculatePrice(storeId, product.getCode(), "MEDIUM", null, List.of());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
