package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    /** 对模型或规则生成的组合执行服务端硬约束校验。 */
    ValidationResult validateCombo(List<MenuItemDTO> products,
                                     CustomerOrderIntentParser.Intent intent,
                                     double totalPrice) {
        List<String> failures = new ArrayList<>();
        log.info("待校验商品: {}", products.stream()
                .map(p -> p.getName() + "(" + safe(p.getCategoryCode()) + ")")
                .toList());

        validateCategories(products, intent, failures);
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

    /** 品类是硬约束：用户点名商品也不能绕过品类校验。 */
    private void validateCategories(List<MenuItemDTO> products,
                                     CustomerOrderIntentParser.Intent intent,
                                     List<String> failures) {
        if (intent.requiredCategories().isEmpty()) return;

        Set<String> allCategories = new LinkedHashSet<>();
        for (MenuItemDTO p : products) {
            String cat = safe(p.getCategoryCode());
            if (!cat.isBlank()) allCategories.add(cat);
        }

        log.info("校验品类: allCategories={}, requiredCategories={}", allCategories, intent.requiredCategories());

        for (String required : intent.requiredCategories()) {
            boolean found = allCategories.stream()
                    .anyMatch(pcat -> matchesCategory(required, pcat));
            log.info("检查品类 {}: found={}", required, found);
            if (!found) {
                failures.add("缺少必需品类: " + catName(required));
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
                if (matchesCategory(excludedCat, safe(p.getCategoryCode()))) {
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

    private boolean matchesCategory(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private String catName(String cat) {
        return Map.of("coffee", "咖啡", "food", "轻食", "dessert", "甜点",
                "tea", "茶饮", "ice", "冰淇淋").getOrDefault(cat, cat);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
