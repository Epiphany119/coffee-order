package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;

import java.util.*;

/**
 * 顾客 Agent 的统一推荐策略。
 *
 * <p>这里的规则同时服务于候选过滤、规则降级和方案生成。LLM 只能在
 * {@link #filterHardConstraints(List, CustomerOrderIntentParser.Intent, int)}
 * 的结果中选择，不能绕过这层策略。</p>
 */
final class CustomerOrderRecommendationPolicy {
    static final int DEFAULT_ITEM_COUNT = 1;
    static final int MAX_OPTION_COUNT = 3;

    List<MenuItemDTO> filterHardConstraints(List<MenuItemDTO> products,
                                            CustomerOrderIntentParser.Intent intent,
                                            int targetItemCount) {
        if (products == null || products.isEmpty()) return List.of();
        CustomerOrderIntentParser.Intent safeIntent = intent == null ? emptyIntent() : intent;
        return products.stream()
                .filter(Objects::nonNull)
                .filter(p -> !matchesExcludedProductOrCategory(p,
                        safeIntent.excludedProducts(), safeIntent.excludedCategories()))
                .filter(p -> !isFillerOnly(p) || explicitlyRequested(p, safeIntent))
                .filter(p -> safeIntent.requiredCategories().isEmpty()
                        || safeIntent.requiredCategories().stream().anyMatch(category -> matchesCategory(p, category)))
                .filter(p -> matchesTemperature(p, safeIntent.temperature()))
                .toList();
    }

    /** 按业务偏好排序；排序完全确定，同分时使用商品 ID/code，绝不依赖随机数或数据库返回顺序。 */
    List<MenuItemDTO> rank(List<MenuItemDTO> products,
                           String userText,
                           Set<String> favoriteCodes,
                           Set<String> favoriteCategories,
                           Set<String> hotNames,
                           Map<Long, Double> ratings,
                           Map<String, Double> semanticScores,
                           CustomerOrderIntentParser.Intent intent,
                           Map<String, Integer> priceRank) {
        if (products == null || products.isEmpty()) return List.of();
        return products.stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> compare(left, right, userText, favoriteCodes,
                        favoriteCategories, hotNames, ratings, semanticScores, intent, priceRank))
                .toList();
    }

    int score(MenuItemDTO product,
               String userText,
               Set<String> favoriteCodes,
               Set<String> favoriteCategories,
               Set<String> hotNames,
               Map<Long, Double> ratings,
               Map<String, Double> semanticScores,
               CustomerOrderIntentParser.Intent intent) {
        if (product == null) return Integer.MIN_VALUE;
        Set<String> codes = favoriteCodes == null ? Set.of() : favoriteCodes;
        Set<String> categories = favoriteCategories == null ? Set.of() : favoriteCategories;
        Set<String> hot = hotNames == null ? Set.of() : hotNames;
        Map<Long, Double> safeRatings = ratings == null ? Map.of() : ratings;
        Map<String, Double> semantic = semanticScores == null ? Map.of() : semanticScores;
        CustomerOrderIntentParser.Intent safeIntent = intent == null ? emptyIntent() : intent;

        int score = 0;
        String category = safe(product.getCategoryCode());
        if (codes.contains(product.getCode())) score += 36;
        if (categories.contains(category)) score += 15;
        if (hot.contains(product.getName())) score += 13;
        if (safeRatings.getOrDefault(product.getId(), 0d) >= 4.2) score += 10;
        if (CustomerOrderIntentCatalog.directlyMentioned(userText, product)) score += 120;

        // 所有口味/场景标签统一走同一个词典，不再为“甜度”单独写一条分支。
        score += CustomerOrderIntentCatalog.preferenceScore(product, safeIntent.preferenceTags());
        score += Math.max(0, (int) Math.round(semantic.getOrDefault(product.getCode(), 0d) * 40));

        if (!safeIntent.requiredCategories().isEmpty()
                && safeIntent.requiredCategories().stream().anyMatch(categoryName -> matchesCategory(product, categoryName))) {
            score += 18;
        }
        if (safeIntent.temperature() == CustomerOrderIntentParser.Temperature.COLD) {
            score += temperatureScore(product, "cold");
        } else if (safeIntent.temperature() == CustomerOrderIntentParser.Temperature.HOT) {
            score += temperatureScore(product, "hot");
        }
        return score;
    }

    boolean matchesCategory(MenuItemDTO product, String category) {
        return CustomerOrderIntentCatalog.matchesCategory(product, category);
    }

    boolean matchesTemperature(MenuItemDTO product, CustomerOrderIntentParser.Temperature temperature) {
        return CustomerOrderIntentCatalog.matchesTemperature(product, temperature);
    }

    boolean matchesExcludedProductOrCategory(MenuItemDTO product,
                                             Set<String> excludedTerms,
                                             Set<String> excludedCategories) {
        return CustomerOrderIntentCatalog.matchesExcludedProductOrCategory(product, excludedTerms, excludedCategories);
    }

    boolean isFillerOnly(MenuItemDTO product) {
        return CustomerOrderIntentCatalog.isFillerOnly(product);
    }

    private boolean explicitlyRequested(MenuItemDTO product, CustomerOrderIntentParser.Intent intent) {
        if (product == null || intent == null || intent.explicitProductNames() == null) return false;
        String name = CustomerOrderIntentCatalog.normalize(product.getName());
        String code = CustomerOrderIntentCatalog.normalize(product.getCode());
        if (name.isBlank() && code.isBlank()) return false;
        return intent.explicitProductNames().stream()
                .filter(Objects::nonNull)
                .map(CustomerOrderIntentCatalog::normalize)
                .filter(term -> !term.isBlank())
                .anyMatch(term -> (!name.isBlank() && (name.contains(term) || term.contains(name)))
                        || (!code.isBlank() && code.equals(term)));
    }

    private int compare(MenuItemDTO left, MenuItemDTO right,
                        String userText,
                        Set<String> favoriteCodes,
                        Set<String> favoriteCategories,
                        Set<String> hotNames,
                        Map<Long, Double> ratings,
                        Map<String, Double> semanticScores,
                        CustomerOrderIntentParser.Intent intent,
                        Map<String, Integer> priceRank) {
        CustomerOrderIntentParser.PricePreference preference = intent == null
                ? CustomerOrderIntentParser.PricePreference.NONE : intent.pricePreference();
        if (preference != null && preference != CustomerOrderIntentParser.PricePreference.NONE) {
            int priceComparison = Integer.compare(
                    priceRank == null ? Integer.MAX_VALUE : priceRank.getOrDefault(left.getCode(), Integer.MAX_VALUE),
                    priceRank == null ? Integer.MAX_VALUE : priceRank.getOrDefault(right.getCode(), Integer.MAX_VALUE));
            if (priceComparison != 0) return priceComparison;
        }

        int scoreComparison = Integer.compare(
                score(right, userText, favoriteCodes, favoriteCategories, hotNames, ratings, semanticScores, intent),
                score(left, userText, favoriteCodes, favoriteCategories, hotNames, ratings, semanticScores, intent));
        if (scoreComparison != 0) return scoreComparison;

        int idComparison = Comparator.nullsLast(Long::compareTo).compare(left.getId(), right.getId());
        if (idComparison != 0) return idComparison;
        int codeComparison = safe(left.getCode()).compareTo(safe(right.getCode()));
        if (codeComparison != 0) return codeComparison;
        return safe(left.getName()).compareTo(safe(right.getName()));
    }

    private int temperatureScore(MenuItemDTO product, String requested) {
        String actual = safe(product.getTemperature()).toLowerCase(Locale.ROOT);
        if (actual.equals(requested) || actual.equals("both")) return 20;
        if (actual.equals(requested.equals("cold") ? "hot" : "cold")) return -18;
        return 0;
    }

    private CustomerOrderIntentParser.Intent emptyIntent() {
        return new CustomerOrderIntentParser.Intent(List.of(), Set.of(), Set.of(),
                CustomerOrderIntentParser.Temperature.ANY, null, false,
                DEFAULT_ITEM_COUNT, CustomerOrderIntentParser.PricePreference.NONE,
                "", List.of(), List.of());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
