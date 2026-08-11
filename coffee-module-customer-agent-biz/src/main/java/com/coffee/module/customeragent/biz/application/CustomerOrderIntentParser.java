package com.coffee.module.customeragent.biz.application;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将自然语言点单需求收敛为可被业务规则执行的约束。
 * 模型只能解释最终方案，不能覆盖这里解析出的指定品类、排除项和预算。
 */
final class CustomerOrderIntentParser {
    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "coffee", List.of("咖啡", "美式", "拿铁", "浓缩", "摩卡", "冷萃", "卡布奇诺"),
            "food", List.of("轻食", "三明治", "沙拉", "贝果", "午餐", "正餐", "咸食", "汉堡", "薯条", "炸鸡", "炸物", "小吃", "吃的", "吃点"),
            "dessert", List.of("甜点", "蛋糕", "甜品", "曲奇", "可颂"),
            "tea", List.of("奶茶", "珍珠奶茶", "果茶", "茶饮"),
            // 门店的冰沙、沙冰和冰淇淋均落在 ice 菜单类目；出现这些词时不能被茶饮收藏覆盖。
            "ice", List.of("冰淇淋", "冰激凌", "冰沙", "沙冰", "思慕雪"));
    // 同时支持“预算 40”“40 元以内”和用户常见的“40 预算”。
    private static final Pattern BUDGET = Pattern.compile("(?:预算|不超过|控制在|最多|小于|<|≤)\\s*([0-9]{1,4})(?:\\s*元)?|([0-9]{1,4})\\s*(?:元)?\\s*(?:预算|以内|以下|左右)|([0-9]{1,4})\\s*元(?:左右|以内|以下)?");
    private static final Pattern ITEM_COUNT = Pattern.compile("([1-3一二三])\\s*(?:个|份|样|种|品)");
    private static final Pattern EXCLUDED_PRODUCT = Pattern.compile("(?:不要|不喝|别|不想要)\\s*([^，。,.！!；;]{1,12})");

    Intent parse(String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        List<String> required = new ArrayList<>();
        Set<String> excluded = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_WORDS.entrySet()) {
            boolean mentioned = false;
            for (String word : entry.getValue()) {
                if (!text.contains(word)) continue;
                mentioned = true;
                if (isNegated(text, word)) excluded.add(entry.getKey());
            }
            if (mentioned && !excluded.contains(entry.getKey())) required.add(entry.getKey());
        }
        required.sort(Comparator.comparingInt(category -> firstIndex(text, CATEGORY_WORDS.get(category))));
        Integer budget = budget(text);
        int itemCount = itemCount(text);
        PricePreference pricePreference = pricePreference(text);
        Temperature temperature = has(text, "冰", "冷", "冰的", "少冰") ? Temperature.COLD : has(text, "热", "暖", "热的") ? Temperature.HOT : Temperature.ANY;
        boolean pairing = has(text, "搭配", "套餐", "组合", "一起", "下午茶", "顺便", "配个", "配一", "饿", "小食");
        Set<String> excludedProducts = excludedProducts(text);
        return new Intent(required, excluded, excludedProducts, temperature, budget, pairing, itemCount, pricePreference, summary(required, excluded, excludedProducts, temperature, budget, pairing, itemCount, pricePreference));
    }

    private boolean isNegated(String text, String word) {
        int index = text.indexOf(word);
        if (index < 0) return false;
        int from = Math.max(0, index - 4);
        String prefix = text.substring(from, index);
        return prefix.contains("不要") || prefix.contains("不喝") || prefix.contains("别") || prefix.contains("不想") || prefix.contains("忌");
    }
    private int firstIndex(String text, List<String> words) { return words.stream().mapToInt(word -> { int i = text.indexOf(word); return i < 0 ? Integer.MAX_VALUE : i; }).min().orElse(Integer.MAX_VALUE); }
    private Integer budget(String text) { Matcher m = BUDGET.matcher(text); if (!m.find()) return null; String value = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3); try { return Integer.parseInt(value); } catch (Exception ignored) { return null; } }
    private int itemCount(String text) { Matcher m = ITEM_COUNT.matcher(text); if (!m.find()) return 0; return switch (m.group(1)) { case "一" -> 1; case "二" -> 2; case "三" -> 3; default -> Integer.parseInt(m.group(1)); }; }
    private PricePreference pricePreference(String text) {
        if (has(text, "最贵", "顶配", "最顶", "豪华")) return PricePreference.MOST_EXPENSIVE;
        if (has(text, "有排面", "请客", "高级", "高档", "贵点", "好一点", "拿得出手")) return PricePreference.PREMIUM;
        if (has(text, "中档", "中等", "适中", "普通", "正常价", "均价")) return PricePreference.MID;
        if (has(text, "便宜", "划算", "省点", "低价", "经济", "性价比")) return PricePreference.CHEAP;
        return PricePreference.NONE;
    }
    private Set<String> excludedProducts(String text) { Matcher m = EXCLUDED_PRODUCT.matcher(text); Set<String> out = new LinkedHashSet<>(); while (m.find()) { String term = m.group(1).trim(); if (!term.isBlank()) out.add(term.replaceAll("^(?:一杯|一份|个)", "")); } return out; }
    private boolean has(String text, String... words) { return Arrays.stream(words).anyMatch(text::contains); }
    private String summary(List<String> required, Set<String> excluded, Set<String> excludedProducts, Temperature temperature, Integer budget, boolean pairing, int itemCount, PricePreference pricePreference) {
        List<String> parts = new ArrayList<>();
        if (!required.isEmpty()) parts.add("指定" + required.stream().map(this::name).reduce((a,b) -> a + "、" + b).orElse(""));
        if (!excluded.isEmpty()) parts.add("排除" + excluded.stream().map(this::name).reduce((a,b) -> a + "、" + b).orElse(""));
        if (!excludedProducts.isEmpty()) parts.add("不要" + String.join("、", excludedProducts));
        if (temperature != Temperature.ANY) parts.add(temperature == Temperature.COLD ? "偏好冰饮" : "偏好热饮");
        if (budget != null) parts.add("预算不高于 ¥" + budget);
        if (pairing) parts.add("需要搭配");
        if (itemCount > 0) parts.add("指定" + itemCount + "件商品");
        if (pricePreference != PricePreference.NONE) parts.add(pricePreference.label);
        return parts.isEmpty() ? "未指定品类，按偏好与门店数据推荐" : String.join("；", parts);
    }
    private String name(String category) { return Map.of("coffee","咖啡","food","轻食","dessert","甜点","tea","茶饮","ice","冰淇淋").getOrDefault(category, category); }

    enum Temperature { ANY, COLD, HOT }
    enum PricePreference { NONE(""), CHEAP("偏好实惠价"), MID("偏好中档价"), PREMIUM("偏好有排面/精品价"), MOST_EXPENSIVE("指定最贵商品"); final String label; PricePreference(String label) { this.label = label; } }
    record Intent(List<String> requiredCategories, Set<String> excludedCategories, Set<String> excludedProducts, Temperature temperature, Integer budget, boolean pairing, int itemCount, PricePreference pricePreference, String summary) { }
}
