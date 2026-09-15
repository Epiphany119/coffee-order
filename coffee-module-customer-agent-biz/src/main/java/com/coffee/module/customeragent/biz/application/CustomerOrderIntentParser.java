package com.coffee.module.customeragent.biz.application;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CustomerOrderIntentParser {
    private static final List<String> ADD_CONNECTORS = List.of("加一", "再来", "再加", "配", "搭配", "配一", "组合", "一起", "顺便", "而且", "还要", "另外", "同时");

    private static final Pattern BUDGET = Pattern.compile("(?:预算|不超过|控制在|最多|小于|<|≤)\\s*([0-9]{1,4})(?:\\s*元)?|([0-9]{1,4})\\s*(?:元)?\\s*(?:预算|以内|以下|左右)|([0-9]{1,4})\\s*元(?:左右|以内|以下)?");
    // “款/种/样”描述推荐的商品种类，不等于用户要购买的件数；推荐方案数量由
    // MAX_OPTION_COUNT 单独控制，避免“推荐三款”被误解析成下单三件。
    private static final Pattern ITEM_COUNT = Pattern.compile("([0-9]{1,2}|[一二三四五六七八九十兩两]+)\\s*(?:个|份|品|杯|碗|块|根|条|片|份儿|件)");
    private static final Pattern EXCLUDED_PRODUCT = Pattern.compile("(?:不要|不喝|别|不想要|不是|并非|不吃|不选)\\s*([^，。,.！!；;]{1,12})");

    Intent parse(String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);

        List<String> allCategoriesInOrder = CustomerOrderIntentCatalog.categoriesFromText(text);
        Set<String> excluded = new LinkedHashSet<>(CustomerOrderIntentCatalog.excludedCategoriesFromText(text));

        List<String> required = allCategoriesInOrder.stream()
                .filter(cat -> !excluded.contains(cat))
                .toList();

        Integer explicitItemCount = explicitItemCount(text);
        int inferredItemCount = Math.max(required.size(), explicitItemCount != null ? explicitItemCount : 0);
        // 未写数量时，一份推荐方案默认购买 1 件；推荐方案数量由应用层单独控制。
        inferredItemCount = inferredItemCount > 0
                ? Math.min(inferredItemCount, 10)
                : CustomerOrderRecommendationPolicy.DEFAULT_ITEM_COUNT;
        boolean multiCategory = required.size() >= 2;
        boolean explicitPairing = has(text, "搭配", "套餐", "组合", "一起", "下午茶", "顺便", "配个", "配一", "饿", "小食", "加一份", "加个", "来份", "来个") || ADD_CONNECTORS.stream().anyMatch(text::contains);

        Integer budget = budget(text);
        PricePreference pricePreference = pricePreference(text);
        Temperature temperature = detectTemperature(text);

        Set<String> excludedProducts = excludedProducts(text);

        boolean pairing = multiCategory || explicitPairing;
        List<String> preferenceTags = CustomerOrderIntentCatalog.canonicalPreferenceTags(text, List.of());

        return new Intent(required, excluded, excludedProducts, temperature, budget, pairing, inferredItemCount,
                pricePreference, summary(required, excluded, excludedProducts, temperature, budget,
                inferredItemCount, pricePreference, preferenceTags), List.of(), preferenceTags);
    }

    private Temperature detectTemperature(String text) {
        return CustomerOrderIntentCatalog.temperatureFromText(text);
    }

    private Integer budget(String text) {
        Matcher m = BUDGET.matcher(text);
        if (!m.find()) return null;
        String value = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer explicitItemCount(String text) {
        Matcher m = ITEM_COUNT.matcher(text);
        int total = 0;
        boolean found = false;
        while (m.find()) {
            if (CustomerOrderIntentCatalog.isNegatedAt(text, m.start())) continue;
            int count = parseQuantity(m.group(1));
            if (count <= 0) continue;
            // 数量本身就是用户的硬约束，不应因为后面没有出现品类词而被丢弃。
            // 例如“我想喝一杯甜一点的”在规则降级时也必须解析为 1 杯。
            found = true;
            total = Math.min(10, total + count);
        }
        return found ? total : null;
    }

    private int parseQuantity(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                return Math.min(10, Integer.parseInt(value));
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return switch (value) {
            case "一" -> 1;
            case "二", "两", "兩" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    private PricePreference pricePreference(String text) {
        if (has(text, "最贵", "顶配", "最顶", "豪华")) return PricePreference.MOST_EXPENSIVE;
        if (has(text, "有排面", "请客", "高级", "高档", "贵点", "好一点", "拿得出手")) return PricePreference.PREMIUM;
        if (has(text, "中档", "中等", "适中", "普通", "正常价", "均价")) return PricePreference.MID;
        if (has(text, "便宜", "划算", "省点", "低价", "经济", "性价比")) return PricePreference.CHEAP;
        return PricePreference.NONE;
    }

    private Set<String> excludedProducts(String text) {
        Matcher m = EXCLUDED_PRODUCT.matcher(text);
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            String term = m.group(1).trim();
            if (!term.isBlank()) out.add(term.replaceAll("^(?:一杯|一份|个)", ""));
        }
        return out;
    }

    private boolean has(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    private String summary(List<String> required, Set<String> excluded, Set<String> excludedProducts,
                           Temperature temperature, Integer budget, int itemCount,
                           PricePreference pricePreference, List<String> preferenceTags) {
        List<String> parts = new ArrayList<>();
        if (!required.isEmpty()) parts.add("指定" + required.stream().map(CustomerOrderIntentCatalog::categoryName).reduce((a, b) -> a + "、" + b).orElse(""));
        if (!excluded.isEmpty()) parts.add("排除" + excluded.stream().map(CustomerOrderIntentCatalog::categoryName).reduce((a, b) -> a + "、" + b).orElse(""));
        if (!excludedProducts.isEmpty()) parts.add("不要" + String.join("、", excludedProducts));
        if (temperature != Temperature.ANY) parts.add(temperature == Temperature.COLD ? "偏好冰饮" : "偏好热饮");
        if (budget != null) parts.add("预算不高于 ¥" + budget);
        if (itemCount > 0) parts.add("指定" + itemCount + "件商品");
        if (pricePreference != PricePreference.NONE) parts.add(pricePreference.label);
        if (preferenceTags != null && !preferenceTags.isEmpty()) parts.add("偏好" + String.join("、", preferenceTags));
        return parts.isEmpty() ? "未指定品类，按偏好与门店数据推荐" : String.join("；", parts);
    }

    enum Temperature { ANY, COLD, HOT }

    enum PricePreference {
        NONE(""), CHEAP("偏好实惠价"), MID("偏好中档价"),
        PREMIUM("偏好有排面/精品价"), MOST_EXPENSIVE("指定最贵商品");
        final String label;
        PricePreference(String label) { this.label = label; }
    }

    record Intent(List<String> requiredCategories, Set<String> excludedCategories, Set<String> excludedProducts,
                  Temperature temperature, Integer budget, boolean pairing, int itemCount,
                  PricePreference pricePreference, String summary,
                  List<String> explicitProductNames, List<String> preferenceTags) {
        Intent(List<String> requiredCategories, Set<String> excludedCategories, Set<String> excludedProducts,
               Temperature temperature, Integer budget, boolean pairing, int itemCount,
               PricePreference pricePreference, String summary,
               List<String> explicitProductNames) {
            this(requiredCategories, excludedCategories, excludedProducts, temperature, budget, pairing,
                    itemCount, pricePreference, summary, explicitProductNames, List.of());
        }

        public Intent {
            requiredCategories = requiredCategories == null ? List.of() : List.copyOf(requiredCategories);
            excludedCategories = excludedCategories == null ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(excludedCategories));
            excludedProducts = excludedProducts == null ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(excludedProducts));
            temperature = temperature == null ? Temperature.ANY : temperature;
            itemCount = itemCount > 0
                    ? Math.min(itemCount, 10) : CustomerOrderRecommendationPolicy.DEFAULT_ITEM_COUNT;
            pricePreference = pricePreference == null ? PricePreference.NONE : pricePreference;
            summary = summary == null ? "" : summary;
            explicitProductNames = explicitProductNames == null ? List.of() : List.copyOf(explicitProductNames);
            preferenceTags = preferenceTags == null ? List.of() : List.copyOf(preferenceTags);
        }
    }
}
