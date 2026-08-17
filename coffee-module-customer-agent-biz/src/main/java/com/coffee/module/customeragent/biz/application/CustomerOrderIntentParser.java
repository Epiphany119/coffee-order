package com.coffee.module.customeragent.biz.application;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CustomerOrderIntentParser {
    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "coffee", List.of("咖啡", "美式", "拿铁", "浓缩", "摩卡", "冷萃", "卡布奇诺", "馥芮白", "flat white"),
            "food", List.of("轻食", "三明治", "沙拉", "贝果", "午餐", "正餐", "咸食", "汉堡", "薯条", "炸鸡", "炸物", "小吃", "吃的", "吃点", "热狗", "鸡肉卷"),
            "dessert", List.of("甜点", "蛋糕", "甜品", "曲奇", "可颂", "芝士蛋糕"),
            "tea", List.of("奶茶", "珍珠奶茶", "果茶", "茶饮", "伯爵茶", "红茶", "绿茶", "普洱"),
            "ice", List.of("冰淇淋", "冰激凌", "冰沙", "沙冰", "思慕雪", "冰饮", "冷饮", "冰的饮料", "冰的喝的"));

    private static final List<String> ADD_CONNECTORS = List.of("加", "加一", "再来", "再加", "配", "搭配", "配一", "组合", "一起", "顺便", "而且", "还要", "另外", "同时");

    private static final Pattern BUDGET = Pattern.compile("(?:预算|不超过|控制在|最多|小于|<|≤)\\s*([0-9]{1,4})(?:\\s*元)?|([0-9]{1,4})\\s*(?:元)?\\s*(?:预算|以内|以下|左右)|([0-9]{1,4})\\s*元(?:左右|以内|以下)?");
    private static final Pattern ITEM_COUNT = Pattern.compile("([1-3一二三兩两])\\s*(?:个|份|样|种|品|杯|碗|块|根|条|片|份儿|件|款)");
    private static final Pattern EXCLUDED_PRODUCT = Pattern.compile("(?:不要|不喝|别|不想要|不是|并非|不吃|不选)\\s*([^，。,.！!；;]{1,12})");

    Intent parse(String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);

        Map<String, Integer> categoryPositions = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_WORDS.entrySet()) {
            int pos = firstIndex(text, entry.getValue());
            if (pos < Integer.MAX_VALUE) {
                categoryPositions.put(entry.getKey(), pos);
            }
        }

        List<String> allCategoriesInOrder = categoryPositions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        Set<String> excluded = new LinkedHashSet<>();
        for (String category : allCategoriesInOrder) {
            List<String> words = CATEGORY_WORDS.get(category);
            for (String word : words) {
                int idx = text.indexOf(word);
                if (idx >= 0 && isNegated(text, idx)) {
                    excluded.add(category);
                }
            }
        }

        List<String> required = allCategoriesInOrder.stream()
                .filter(cat -> !excluded.contains(cat))
                .toList();

        Integer explicitItemCount = explicitItemCount(text);
        int inferredItemCount = Math.max(required.size(), explicitItemCount != null ? explicitItemCount : 0);
        boolean multiCategory = required.size() >= 2;
        boolean explicitPairing = has(text, "搭配", "套餐", "组合", "一起", "下午茶", "顺便", "配个", "配一", "饿", "小食", "加一份", "加个", "来份", "来个") || ADD_CONNECTORS.stream().anyMatch(text::contains);

        Integer budget = budget(text);
        PricePreference pricePreference = pricePreference(text);
        Temperature temperature = detectTemperature(text);

        Set<String> excludedProducts = excludedProducts(text);

        boolean pairing = multiCategory || explicitPairing;

        return new Intent(required, excluded, excludedProducts, temperature, budget, pairing, inferredItemCount, pricePreference, summary(required, excluded, excludedProducts, temperature, budget, inferredItemCount, pricePreference), List.of());
    }

    private Temperature detectTemperature(String text) {
        boolean cold = has(text, "冰", "冷", "冰的", "少冰", "去冰", "冰镇");
        boolean hot = has(text, "热", "暖", "热的", "温的");
        if (cold && !hot) return Temperature.COLD;
        if (hot && !cold) return Temperature.HOT;
        if (cold) return Temperature.COLD;
        if (hot) return Temperature.HOT;
        return Temperature.ANY;
    }

    private boolean isNegated(String text, int wordIndex) {
        int from = Math.max(0, wordIndex - 6);
        String prefix = text.substring(from, wordIndex);
        return prefix.contains("不要") || prefix.contains("不喝") || prefix.contains("别") || prefix.contains("不想") || prefix.contains("忌") || prefix.contains("不是") || prefix.contains("并非") || prefix.contains("不吃") || prefix.contains("不选");
    }

    private int firstIndex(String text, List<String> words) {
        return words.stream().mapToInt(word -> {
            int i = text.indexOf(word);
            return i < 0 ? Integer.MAX_VALUE : i;
        }).min().orElse(Integer.MAX_VALUE);
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
        if (!m.find()) return null;
        String num = m.group(1);
        int count = switch (num) {
            case "一" -> 1; case "二", "两" -> 2; case "三" -> 3;
            default -> {
                try { yield Integer.parseInt(num); } catch (Exception e) { yield 0; }
            }
        };
        int matchEnd = m.end();
        String after = text.substring(Math.min(matchEnd, text.length()), Math.min(matchEnd + 10, text.length()));
        boolean isCategorySpec = false;
        for (Map.Entry<String, List<String>> entry : CATEGORY_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (after.contains(word)) { isCategorySpec = true; break; }
            }
            if (isCategorySpec) break;
        }
        if (isCategorySpec) return count;
        boolean hasConnector = false;
        for (String conn : ADD_CONNECTORS) {
            int connIdx = text.indexOf(conn);
            if (connIdx >= 0 && connIdx < m.start()) { hasConnector = true; break; }
        }
        return hasConnector ? count : null;
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

    private String summary(List<String> required, Set<String> excluded, Set<String> excludedProducts, Temperature temperature, Integer budget, int itemCount, PricePreference pricePreference) {
        List<String> parts = new ArrayList<>();
        if (!required.isEmpty()) parts.add("指定" + required.stream().map(this::name).reduce((a, b) -> a + "、" + b).orElse(""));
        if (!excluded.isEmpty()) parts.add("排除" + excluded.stream().map(this::name).reduce((a, b) -> a + "、" + b).orElse(""));
        if (!excludedProducts.isEmpty()) parts.add("不要" + String.join("、", excludedProducts));
        if (temperature != Temperature.ANY) parts.add(temperature == Temperature.COLD ? "偏好冰饮" : "偏好热饮");
        if (budget != null) parts.add("预算不高于 ¥" + budget);
        if (itemCount >= required.size() && itemCount > 0) parts.add("指定" + itemCount + "件商品");
        if (pricePreference != PricePreference.NONE) parts.add(pricePreference.label);
        return parts.isEmpty() ? "未指定品类，按偏好与门店数据推荐" : String.join("；", parts);
    }

    private String name(String category) {
        return Map.of("coffee", "咖啡", "food", "轻食", "dessert", "甜点", "tea", "茶饮", "ice", "冰淇淋").getOrDefault(category, category);
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
                  List<String> explicitProductNames) { }
}
