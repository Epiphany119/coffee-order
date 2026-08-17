package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

final class IntentValidator {
    private static final Logger log = LoggerFactory.getLogger(IntentValidator.class);

    record ValidationResult(boolean valid, List<String> reasons) {
        static ValidationResult pass() {
            return new ValidationResult(true, List.of());
        }

        static ValidationResult fail(String... reasons) {
            return new ValidationResult(false, Arrays.asList(reasons));
        }
    }

    /**
     * 校验组合，支持精确匹配商品豁免
     *
     * @param products         选中的商品列表
     * @param intent           意图
     * @param totalPrice       总价
     * @param explicitProductNames 用户明确点名的商品关键词（精确匹配的商品豁免品类校验）
     */
    ValidationResult validateCombo(List<MenuItemDTO> products,
                                     CustomerOrderIntentParser.Intent intent,
                                     double totalPrice,
                                     List<String> explicitProductNames) {
        List<String> failures = new ArrayList<>();

        // 找出精确匹配的商品（用户点名的）
        Set<String> exactMatchedCodes = new HashSet<>();
        if (explicitProductNames != null && !explicitProductNames.isEmpty()) {
            for (String keyword : explicitProductNames) {
                if (keyword == null || keyword.isBlank()) continue;
                for (MenuItemDTO p : products) {
                    if (p.getName() != null && p.getName().contains(keyword)) {
                        exactMatchedCodes.add(p.getCode());
                    }
                }
            }
        }

        List<MenuItemDTO> exactMatched = products.stream()
                .filter(p -> exactMatchedCodes.contains(p.getCode()))
                .collect(Collectors.toList());

        List<MenuItemDTO> others = products.stream()
                .filter(p -> !exactMatchedCodes.contains(p.getCode()))
                .collect(Collectors.toList());

        log.info("精确匹配商品（豁免品类校验）: {}", exactMatched.stream()
                .map(p -> p.getName() + "(" + safe(p.getCategoryCode()) + ")")
                .collect(Collectors.joining(", ")));
        log.info("其他商品（需品类校验）: {}", others.stream()
                .map(p -> p.getName() + "(" + safe(p.getCategoryCode()) + ")")
                .collect(Collectors.joining(", ")));

        // 精确匹配的商品豁免品类校验，只校验其他商品
        validateCategories(others, exactMatched, intent, failures);
        validateCount(products, intent, failures);
        validateBudget(totalPrice, intent, failures);
        validateExcluded(products, intent, failures);
        validateTemperature(products, intent, failures);

        if (failures.isEmpty()) {
            return ValidationResult.pass();
        } else {
            log.warn("组合校验失败: {}", String.join("; ", failures));
            return new ValidationResult(false, failures);
        }
    }

    /**
     * 校验品类：结合精确匹配商品 + 其他商品的品类
     */
    private void validateCategories(List<MenuItemDTO> others,
                                     List<MenuItemDTO> exactMatched,
                                     CustomerOrderIntentParser.Intent intent,
                                     List<String> failures) {
        if (!intent.pairing() || intent.requiredCategories().isEmpty()) return;

        // 收集所有商品的品类（精确匹配 + 其他）
        Set<String> allCategories = new LinkedHashSet<>();
        for (MenuItemDTO p : others) {
            String cat = safe(p.getCategoryCode());
            if (!cat.isBlank()) allCategories.add(cat);
        }
        for (MenuItemDTO p : exactMatched) {
            String cat = safe(p.getCategoryCode());
            if (!cat.isBlank()) allCategories.add(cat);
        }

        log.info("校验品类: allCategories={}, requiredCategories={}", allCategories, intent.requiredCategories());

        // 精确匹配商品已经覆盖了某些需求品类的话，跳过校验
        Set<String> coveredByExact = new HashSet<>();
        for (String required : intent.requiredCategories()) {
            for (MenuItemDTO ep : exactMatched) {
                if (matchesCategoryLoose(required, safe(ep.getCategoryCode()))) {
                    coveredByExact.add(required);
                    log.info("品类 {} 已被精确匹配商品 {} 覆盖，跳过校验", required, ep.getName());
                    break;
                }
            }
        }

        for (String required : intent.requiredCategories()) {
            if (coveredByExact.contains(required)) continue;

            boolean found = allCategories.stream()
                    .anyMatch(pcat -> matchesCategoryLoose(required, pcat));
            log.info("检查品类 {}: found={}", required, found);
            if (!found) {
                failures.add("缺少必需品类: " + catName(required));
            }
        }

        if (intent.requiredCategories().size() >= 2) {
            boolean hasFood = allCategories.stream()
                    .anyMatch(c -> matchesCategoryLoose("food", c) || matchesCategoryLoose("dessert", c));
            boolean hasDrink = allCategories.stream()
                    .anyMatch(c -> matchesCategoryLoose("coffee", c) || matchesCategoryLoose("tea", c) || matchesCategoryLoose("ice", c));
            if (!hasFood && intent.requiredCategories().stream().anyMatch(r -> matchesCategoryLoose("food", r) || matchesCategoryLoose("dessert", r))) {
                failures.add("组合缺少食品类商品");
            }
            if (!hasDrink && intent.requiredCategories().stream().anyMatch(r -> matchesCategoryLoose("coffee", r) || matchesCategoryLoose("tea", r) || matchesCategoryLoose("ice", r))) {
                failures.add("组合缺少饮品类商品");
            }
        }
    }

    private void validateCount(List<MenuItemDTO> products,
                                CustomerOrderIntentParser.Intent intent,
                                List<String> failures) {
        int actualCount = products.size();
        int expectedCount = intent.itemCount();
        if (expectedCount > 0 && actualCount != expectedCount) {
            failures.add("商品数量 " + actualCount + " 与期望 " + expectedCount + " 不匹配");
        }
    }

    private void validateBudget(double totalPrice,
                                 CustomerOrderIntentParser.Intent intent,
                                 List<String> failures) {
        if (intent.budget() != null && totalPrice > intent.budget() + 0.01) {
            failures.add("总价 ¥" + String.format(Locale.ROOT, "%.1f", totalPrice) +
                    " 超出预算 ¥" + intent.budget());
        }
    }

    private void validateExcluded(List<MenuItemDTO> products,
                                   CustomerOrderIntentParser.Intent intent,
                                   List<String> failures) {
        for (MenuItemDTO p : products) {
            String name = safe(p.getName()).toLowerCase(Locale.ROOT);
            String desc = safe(p.getDescription()).toLowerCase(Locale.ROOT);
            String full = name + " " + desc;

            for (String excluded : intent.excludedProducts()) {
                if (!excluded.isBlank() && full.contains(excluded.toLowerCase(Locale.ROOT))) {
                    failures.add("包含排除商品: " + p.getName() + " (排除词: " + excluded + ")");
                }
            }

            for (String excludedCat : intent.excludedCategories()) {
                if (matchesCategoryLoose(excludedCat, safe(p.getCategoryCode()))) {
                    failures.add("包含排除品类: " + p.getName() + " (排除品类: " + catName(excludedCat) + ")");
                }
            }
        }
    }

    private void validateTemperature(List<MenuItemDTO> products,
                                      CustomerOrderIntentParser.Intent intent,
                                      List<String> failures) {
        if (intent.temperature() == null || intent.temperature() == CustomerOrderIntentParser.Temperature.ANY) {
            return;
        }

        for (MenuItemDTO p : products) {
            String temp = safe(p.getTemperature()).toUpperCase(Locale.ROOT);
            if (intent.temperature() == CustomerOrderIntentParser.Temperature.COLD && "HOT".equals(temp)) {
                failures.add("冰饮偏好下包含热饮: " + p.getName());
            }
            if (intent.temperature() == CustomerOrderIntentParser.Temperature.HOT && "COLD".equals(temp)) {
                failures.add("热饮偏好下包含冰饮: " + p.getName());
            }
        }
    }

    /**
     * 宽松品类匹配：支持跨品类匹配
     *
     * 策略：
     * 1. 精确匹配：food=food, dessert=dessert, coffee=coffee, ...
     * 2. 宽松匹配：food 可以匹配 dessert, coffee（因为商家可能把汉堡放在咖啡区）
     * 3. 全局兜底：任何品类都可以匹配（因为商家分类可能不一致）
     */
    private boolean matchesCategoryLoose(String expected, String actual) {
        if (expected == null || actual == null) return false;
        if (expected.equalsIgnoreCase(actual)) return true;

        // 精确匹配优先
        boolean strict = switch (expected.toLowerCase(Locale.ROOT)) {
            case "food" -> actual.equalsIgnoreCase("food") || actual.equalsIgnoreCase("dessert");
            case "dessert" -> actual.equalsIgnoreCase("dessert") || actual.equalsIgnoreCase("food");
            case "drink", "coffee", "tea", "ice" ->
                    actual.equalsIgnoreCase("coffee") || actual.equalsIgnoreCase("tea") || actual.equalsIgnoreCase("ice");
            default -> false;
        };
        if (strict) return true;

        // 宽松匹配：food 可以匹配 coffee（汉堡在咖啡区）
        // ice 可以匹配 coffee（冰沙在咖啡区）
        boolean loose = switch (expected.toLowerCase(Locale.ROOT)) {
            case "food" -> actual.equalsIgnoreCase("coffee") || actual.equalsIgnoreCase("tea") || actual.equalsIgnoreCase("ice");
            case "dessert" -> actual.equalsIgnoreCase("coffee") || actual.equalsIgnoreCase("tea");
            case "ice" -> actual.equalsIgnoreCase("coffee") || actual.equalsIgnoreCase("food") || actual.equalsIgnoreCase("dessert");
            case "coffee" -> actual.equalsIgnoreCase("food") || actual.equalsIgnoreCase("dessert");
            default -> false;
        };
        if (loose) {
            log.info("宽松匹配: expected={}, actual={}, matched=true", expected, actual);
            return true;
        }

        // 全局兜底：如果预期品类在已知列表中，但实际品类不在任何预期映射中，仍然返回 false
        // 这样可以避免完全不相关的品类误匹配
        return false;
    }

    private String catName(String cat) {
        return Map.of("coffee", "咖啡", "food", "轻食", "dessert", "甜点",
                "tea", "茶饮", "ice", "冰淇淋").getOrDefault(cat, cat);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
