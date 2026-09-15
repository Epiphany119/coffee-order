package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;

import java.util.*;

/**
 * 顾客点单意图的统一词典和商品属性判断。
 *
 * <p>候选检索、规则推荐以及最终校验必须使用同一套分类和排除规则，
 * 不能在不同类里各维护一份关键词，否则同一个商品可能在候选阶段被认为
 * 是饮品，在校验阶段又被认为不是饮品。</p>
 */
final class CustomerOrderIntentCatalog {
    static final String DRINK_CATEGORY = "drink";
    static final Set<String> DRINK_CATEGORIES = Set.of("coffee", "tea", "ice");

    private static final Map<String, List<String>> CATEGORY_WORDS = orderedMap(
            "coffee", List.of("咖啡", "美式", "拿铁", "浓缩", "摩卡", "冷萃", "卡布奇诺", "馥芮白", "玛奇朵", "flat white"),
            // “吃/吃点”只是动作上下文，不能直接把所有甜味需求判成 food；
            // 具体食品词才属于 food 品类。
            "food", List.of("轻食", "三明治", "沙拉", "贝果", "午餐", "正餐", "咸食", "汉堡", "薯条", "炸鸡", "炸物", "小吃", "热狗", "鸡肉卷", "卷饼", "塔可", "披萨", "面包", "主食"),
            "dessert", List.of("甜点", "蛋糕", "甜品", "曲奇", "可颂", "芝士蛋糕", "布丁", "马卡龙", "慕斯"),
            "tea", List.of("奶茶", "珍珠奶茶", "果茶", "茶饮", "茶", "伯爵茶", "红茶", "绿茶", "普洱", "乌龙", "茉莉", "抹茶", "柠檬茶", "手打柠檬"),
            "ice", List.of("冰淇淋", "冰激凌", "冰沙", "沙冰", "思慕雪", "刨冰", "冰饮", "冷饮", "冰的饮料", "冰的喝的")
    );

    /** 只有这些泛化词被否定时，才排除整个品类；“不要拿铁/奶茶”只排除具体商品词。 */
    private static final Map<String, List<String>> BROAD_CATEGORY_WORDS = orderedMap(
            "coffee", List.of("咖啡"),
            "food", List.of("轻食", "食物", "食品", "午餐", "正餐", "主食"),
            "dessert", List.of("甜点", "甜品"),
            "tea", List.of("茶饮", "茶"),
            "ice", List.of("冰饮", "冷饮")
    );

    private static final List<String> NEGATION_MARKERS = List.of(
            "不需要", "不想要", "不想", "不要", "不喝", "不吃", "不选", "不含",
            "不喜欢", "不爱", "讨厌", "并非", "不是", "去掉", "去除", "排除", "拒绝", "别", "忌", "无", "不"
    );

    private static final Map<String, PreferenceRule> PREFERENCE_RULES = orderedPreferenceRules();

    private CustomerOrderIntentCatalog() {
    }

    /** 从原始话术中提取明确品类；drink 是虚拟品类，代表 coffee/tea/ice。 */
    static List<String> categoriesFromText(String raw) {
        String text = lower(raw);
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_WORDS.entrySet()) {
            int position = firstPositiveCategoryIndex(text, entry.getValue());
            if (position < Integer.MAX_VALUE) {
                positions.put(entry.getKey(), position);
            }
        }

        boolean hasConcreteDrink = positions.keySet().stream().anyMatch(DRINK_CATEGORIES::contains);
        if (positiveDrinkSignal(text) && !hasConcreteDrink) {
            positions.put(DRINK_CATEGORY, firstPositiveCategoryIndex(text,
                    List.of("喝", "饮品", "饮料", "来杯", "饮用", "一杯", "杯")));
        }

        // “我想吃点甜的”是甜点意图，而不是任意轻食；只有没有明确品类时才补充这个上下文判断。
        boolean hasFood = positions.containsKey("food");
        boolean hasDessert = positions.containsKey("dessert");
        if (!hasFood && !hasDessert && positiveEatSignal(text) && containsAny(text, "甜", "甜味", "甜的", "甜一点")) {
            positions.put("dessert", firstPositiveIndex(text, List.of("甜", "甜味", "甜的", "甜一点")));
        }

        return positions.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .toList();
    }

    static Set<String> excludedCategoriesFromText(String raw) {
        String text = lower(raw);
        Set<String> excluded = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : BROAD_CATEGORY_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (containsCategoryNegated(text, List.of(word))) {
                    excluded.add(entry.getKey());
                }
            }
        }
        boolean hasPositiveConcreteDrink = CATEGORY_WORDS.entrySet().stream()
                .filter(entry -> DRINK_CATEGORIES.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(word -> firstPositiveCategoryIndex(text, List.of(word)) < Integer.MAX_VALUE);
        boolean hasPositiveGenericDrink = firstPositiveCategoryIndex(text, List.of("饮品", "饮料")) < Integer.MAX_VALUE;
        // “不想喝甜的茶/饮品”中的“不想喝”是否定动作，不是否定整类饮品；
        // 只有没有任何正向饮品品类时，才把 drink 作为排除品类。
        if (!hasPositiveConcreteDrink && !hasPositiveGenericDrink
                && containsCategoryNegated(text, List.of("喝", "饮品", "饮料", "来杯", "饮用", "一杯", "杯"))) {
            excluded.add(DRINK_CATEGORY);
        }
        return excluded;
    }

    /** 从已经被原文验证过的 avoid 词中识别“整类排除”，避免把“甜的饮品”当成排除所有饮品。 */
    static Set<String> explicitlyExcludedCategoriesFromTerm(String term) {
        String text = lower(term).trim();
        if (text.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : BROAD_CATEGORY_WORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(word -> text.equals(word))) {
                result.add(entry.getKey());
            }
        }
        if (text.equals("饮品") || text.equals("饮料")) result.add(DRINK_CATEGORY);
        return result;
    }

    /** 从用户原文提取温度硬约束，忽略“不冰/不要热”等被否定的词。 */
    static CustomerOrderIntentParser.Temperature temperatureFromText(String raw) {
        String text = lower(raw);
        int coldIndex = firstPositiveIndex(text, List.of("冰", "冷", "少冰", "去冰", "冰镇"));
        int hotIndex = firstPositiveIndex(text, List.of("热", "暖", "温的"));
        boolean coldNegated = containsNegated(text, List.of("冰", "冷", "少冰", "去冰", "冰镇"));
        boolean hotNegated = containsNegated(text, List.of("热", "暖", "温的"));
        if (coldIndex == Integer.MAX_VALUE) {
            if (hotIndex != Integer.MAX_VALUE) return CustomerOrderIntentParser.Temperature.HOT;
            if (coldNegated && !hotNegated) return CustomerOrderIntentParser.Temperature.HOT;
            return CustomerOrderIntentParser.Temperature.ANY;
        }
        if (hotIndex == Integer.MAX_VALUE) return CustomerOrderIntentParser.Temperature.COLD;

        if (containsAny(text, "都可以", "都行", "随便", "不挑", "不限", "还是", "或者", "或")) {
            return CustomerOrderIntentParser.Temperature.ANY;
        }
        // 同时出现冷热且不是“都可以”时，以后出现的明确要求为准。
        return coldIndex > hotIndex
                ? CustomerOrderIntentParser.Temperature.COLD
                : CustomerOrderIntentParser.Temperature.HOT;
    }

    /** 将原始文本和 LLM 返回的已被原文验证过的标签统一成规范标签。 */
    static List<String> canonicalPreferenceTags(String raw, Collection<String> suppliedTags) {
        Set<String> tags = new LinkedHashSet<>();
        addCanonicalTags(tags, raw);
        if (suppliedTags != null) {
            for (String supplied : suppliedTags) {
                if (supplied == null || supplied.isBlank()) continue;
                String value = lower(supplied).trim();
                boolean positive = containsPositiveAny(lower(raw), List.of(value));
                boolean negative = containsNegated(lower(raw), List.of(value));
                if (!positive && !negative) continue;
                boolean recognized = false;
                for (Map.Entry<String, PreferenceRule> entry : PREFERENCE_RULES.entrySet()) {
                    if (containsAny(value, entry.getValue().aliases())) {
                        tags.add(positive ? entry.getKey() : "avoid_" + entry.getKey());
                        recognized = true;
                    }
                }
                // 未知标签仍保留，但只允许使用已出现在用户原文中的短词；它会走通用词面匹配。
                if (!recognized && value.length() <= 40) tags.add(positive ? value : "avoid_" + value);
            }
        }
        return List.copyOf(tags);
    }

    static int preferenceScore(MenuItemDTO product, Collection<String> tags) {
        if (product == null || tags == null || tags.isEmpty()) return 0;
        String productText = productText(product);
        int score = 0;
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) continue;
            boolean avoid = tag.startsWith("avoid_");
            String canonicalTag = avoid ? tag.substring("avoid_".length()) : tag;
            PreferenceRule rule = PREFERENCE_RULES.get(canonicalTag);
            if (rule == null) {
                if (productText.contains(lower(canonicalTag))) score += avoid ? -14 : 14;
                continue;
            }
            int matched = 0;
            if (containsAny(productText, rule.positiveTerms())) matched += rule.weight();
            if (containsAny(productText, rule.negativeTerms())) matched -= rule.weight();
            score += avoid ? -matched : matched;
        }
        return score;
    }

    static boolean matchesCategory(MenuItemDTO product, String category) {
        if (product == null || category == null) return false;
        String actual = lower(product.getCategoryCode());
        if (DRINK_CATEGORY.equalsIgnoreCase(category)) return DRINK_CATEGORIES.contains(actual);
        return category.equalsIgnoreCase(actual);
    }

    static boolean matchesTemperature(MenuItemDTO product, CustomerOrderIntentParser.Temperature temperature) {
        if (product == null || temperature == null || temperature == CustomerOrderIntentParser.Temperature.ANY) return true;
        String actual = lower(product.getTemperature());
        if (temperature == CustomerOrderIntentParser.Temperature.COLD) return !"hot".equals(actual);
        if (temperature == CustomerOrderIntentParser.Temperature.HOT) return !"cold".equals(actual);
        return true;
    }

    static boolean matchesExcludedProductOrCategory(MenuItemDTO product,
                                                     Set<String> excludedTerms,
                                                     Set<String> excludedCategories) {
        if (product == null) return true;
        String name = lower(product.getName());
        String description = lower(product.getDescription());
        String full = name + " " + description;

        if (excludedTerms != null) {
            for (String term : excludedTerms) {
                String normalized = lower(term).trim();
                if (!normalized.isBlank() && (full.contains(normalized)
                        || normalized.contains(name) || name.contains(normalized))) {
                    return true;
                }
            }
        }
        if (excludedCategories != null) {
            for (String category : excludedCategories) {
                if (matchesCategory(product, category)) return true;
                for (String keyword : CATEGORY_WORDS.getOrDefault(category, List.of())) {
                    String normalized = lower(keyword);
                    if (name.contains(normalized) || description.contains(normalized)) return true;
                }
            }
        }
        return false;
    }

    static String categoryName(String category) {
        return Map.of("coffee", "咖啡", "food", "轻食", "dessert", "甜点",
                "tea", "茶饮", "ice", "冰饮", DRINK_CATEGORY, "饮品")
                .getOrDefault(category, category);
    }

    static boolean positiveDrinkSignal(String text) {
        return firstPositiveCategoryIndex(lower(text),
                List.of("喝", "饮品", "饮料", "来杯", "饮用", "一杯", "杯")) < Integer.MAX_VALUE;
    }

    static boolean isNegatedAt(String text, int index) {
        return isNegated(lower(text), index);
    }

    static boolean directlyMentioned(String text, MenuItemDTO product) {
        String query = normalize(text);
        String name = normalize(product == null ? "" : product.getName());
        String code = normalize(product == null ? "" : product.getCode());
        if ((!name.isBlank() && query.contains(name)) || (!code.isBlank() && query.contains(code))) return true;
        return (name.contains("汉堡") && query.contains("汉堡"))
                || (name.contains("薯条") && query.contains("薯条"))
                || (name.contains("健达") && (query.contains("健达") || query.contains("奇趣蛋")));
    }

    static boolean isFillerOnly(MenuItemDTO product) {
        if (product == null || product.getTopup() == null || product.getTopup() != 1) return false;
        String text = normalize(product.getName() + " " + product.getCode() + " " + product.getDescription());
        return containsAny(text, "小料", "珍珠", "椰果", "芋圆", "矿泉水", "气泡水");
    }

    static String productText(MenuItemDTO product) {
        if (product == null) return "";
        return lower(String.join(" ", safe(product.getName()), safe(product.getDescription()),
                safe(product.getCategoryCode()), safe(product.getTemperature())));
    }

    static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }

    static boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (word != null && !word.isBlank() && text.contains(word.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, Collection<String> words) {
        if (text == null || words == null) return false;
        for (String word : words) {
            if (word != null && !word.isBlank() && text.contains(word.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static void addCanonicalTags(Set<String> tags, String raw) {
        String text = lower(raw);
        for (Map.Entry<String, PreferenceRule> entry : PREFERENCE_RULES.entrySet()) {
            if (containsPositiveAny(text, entry.getValue().aliases())) tags.add(entry.getKey());
            if (containsNegated(text, entry.getValue().aliases())) tags.add("avoid_" + entry.getKey());
        }
    }

    private static boolean positiveEatSignal(String text) {
        return firstPositiveIndex(text, List.of("吃", "食物", "餐", "小食")) < Integer.MAX_VALUE;
    }

    private static boolean containsNegated(String text, List<String> words) {
        for (String word : words) {
            int from = 0;
            while (from < text.length()) {
                int index = text.indexOf(word, from);
                if (index < 0) break;
                if (isNegated(text, index)) return true;
                from = index + Math.max(1, word.length());
            }
        }
        return false;
    }

    private static boolean containsPositiveAny(String text, Collection<String> words) {
        if (text == null || words == null) return false;
        for (String word : words) {
            int from = 0;
            while (from < text.length()) {
                int index = text.indexOf(word, from);
                if (index < 0) break;
                if (!isNegated(text, index)) return true;
                from = index + Math.max(1, word.length());
            }
        }
        return false;
    }

    private static int firstPositiveIndex(String text, Collection<String> words) {
        int best = Integer.MAX_VALUE;
        for (String word : words) {
            int from = 0;
            while (from < text.length()) {
                int index = text.indexOf(word, from);
                if (index < 0) break;
                if (!isNegated(text, index)) {
                    best = Math.min(best, index);
                    break;
                }
                from = index + Math.max(1, word.length());
            }
        }
        return best;
    }

    private static int firstPositiveCategoryIndex(String text, Collection<String> words) {
        int best = Integer.MAX_VALUE;
        for (String word : words) {
            int from = 0;
            while (from < text.length()) {
                int index = text.indexOf(word, from);
                if (index < 0) break;
                if (!isCategoryNegated(text, index)) {
                    best = Math.min(best, index);
                    break;
                }
                from = index + Math.max(1, word.length());
            }
        }
        return best;
    }

    private static boolean containsCategoryNegated(String text, Collection<String> words) {
        for (String word : words) {
            int from = 0;
            while (from < text.length()) {
                int index = text.indexOf(word, from);
                if (index < 0) break;
                if (isCategoryNegated(text, index)) return true;
                from = index + Math.max(1, word.length());
            }
        }
        return false;
    }

    /**
     * 判断否定是否针对品类本身，而不是针对“甜的/冰的/大杯”等品类修饰词。
     * 这让“不要甜的饮品”仍然保留 drink 候选，只把 sweet 作为排除偏好。
     */
    private static boolean isCategoryNegated(String text, int wordIndex) {
        if (!isNegated(text, wordIndex)) return false;
        int markerStart = lastNegationMarkerStart(text, wordIndex);
        if (markerStart < 0) return true;

        String marker = text.substring(markerStart,
                Math.min(text.length(), markerStart + negationMarkerLength(text, markerStart, wordIndex)));
        int contentStart = markerStart + marker.length();
        if (contentStart > wordIndex) return true;
        String between = text.substring(contentStart, wordIndex);
        if (between.isBlank()) return true;

        if (containsPreferenceModifier(between)
                || containsAny(between, "的", "地", "得", "太", "更", "较", "很", "非常", "一点", "一些")) {
            return false;
        }
        if (containsAny(between, "和", "或", "、", "以及", "还有")) {
            String remaining = removeCategoryWordsAndConnectors(between);
            return remaining.isBlank();
        }
        return onlyActionAndQuantity(between);
    }

    private static int lastNegationMarkerStart(String text, int wordIndex) {
        int best = -1;
        int bestLength = 0;
        for (String marker : NEGATION_MARKERS) {
            int start = text.lastIndexOf(marker, Math.max(0, wordIndex - 1));
            if (start >= 0 && start + marker.length() <= wordIndex
                    && (start > best || (start == best && marker.length() > bestLength))) {
                best = start;
                bestLength = marker.length();
            }
        }
        return best;
    }

    private static int negationMarkerLength(String text, int markerStart, int wordIndex) {
        int bestLength = 0;
        for (String marker : NEGATION_MARKERS) {
            if (text.startsWith(marker, markerStart)
                    && markerStart + marker.length() <= wordIndex
                    && marker.length() > bestLength) {
                bestLength = marker.length();
            }
        }
        return bestLength;
    }

    private static boolean containsPreferenceModifier(String text) {
        for (PreferenceRule rule : PREFERENCE_RULES.values()) {
            if (containsAny(text, rule.aliases()) || containsAny(text, rule.positiveTerms())
                    || containsAny(text, rule.negativeTerms())) return true;
        }
        return false;
    }

    private static String removeCategoryWordsAndConnectors(String text) {
        String remaining = text;
        for (List<String> words : CATEGORY_WORDS.values()) {
            for (String word : words) remaining = remaining.replace(word, "");
        }
        for (String word : List.of("喝", "吃", "要", "想", "选", "含", "来", "点", "买",
                "一", "二", "两", "三", "四", "五", "个", "杯", "份", "样", "种", "品", "件", "款",
                "和", "或", "、", "以及", "还有")) {
            remaining = remaining.replace(word, "");
        }
        return remaining;
    }

    private static boolean onlyActionAndQuantity(String text) {
        return removeCategoryWordsAndConnectors(text).isBlank();
    }

    private static boolean isNegated(String text, int wordIndex) {
        int boundary = lastSentenceBoundary(text, wordIndex);
        int markerStart = lastNegationMarkerStart(text, wordIndex);
        if (markerStart < Math.max(boundary + 1, wordIndex - 8)) return false;

        int markerLength = negationMarkerLength(text, markerStart, wordIndex);
        if (markerLength <= 0) return false;
        String between = text.substring(markerStart + markerLength, wordIndex);
        if (between.isBlank()) return true;

        // 否定通常只作用于紧邻的一个口味/冷热短语。“不要太苦的热咖啡”中“不”
        // 作用于“苦”，不能把后面的“热”也误判成否定。
        if (containsAny(between, "的", "冰", "冷", "热", "暖", "温")
                || containsPreferenceModifier(between)) return false;
        return true;
    }

    private static int lastSentenceBoundary(String text, int wordIndex) {
        int boundary = -1;
        for (char delimiter : new char[]{'，', ',', '。', '.', '！', '!', '？', '?', '；', ';'}) {
            boundary = Math.max(boundary, text.lastIndexOf(delimiter, Math.max(0, wordIndex - 1)));
        }
        return boundary;
    }

    private static String lower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> orderedMap(Object... values) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], List.copyOf((List<String>) values[i + 1]));
        }
        return result;
    }

    private static Map<String, PreferenceRule> orderedPreferenceRules() {
        Map<String, PreferenceRule> result = new LinkedHashMap<>();
        result.put("sweet", new PreferenceRule(
                List.of("甜", "甜味", "甜的", "甜一点", "偏甜", "焦糖", "巧克力", "蜂蜜", "香草", "高糖"),
                List.of("无糖", "低糖", "少糖", "不甜", "苦"), 20));
        result.put("low_sugar", new PreferenceRule(
                List.of("低糖", "少糖", "无糖", "不甜", "不太甜", "清淡"),
                List.of("甜腻", "高糖"), 20));
        result.put("bitter", new PreferenceRule(
                List.of("苦", "醇厚", "浓郁", "深烘", "咖啡味重"),
                List.of("清爽", "果香", "甜腻"), 18));
        result.put("refreshing", new PreferenceRule(
                List.of("清爽", "清新", "轻盈", "解腻", "果香", "柠檬"),
                List.of("浓郁", "奶油", "甜腻"), 18));
        result.put("milky", new PreferenceRule(
                List.of("奶香", "奶味", "牛奶", "燕麦奶", "奶油"),
                List.of("纯咖啡", "美式"), 16));
        result.put("fruity", new PreferenceRule(
                List.of("果香", "果味", "水果", "柑橘", "莓", "桃", "芒果", "葡萄", "柠檬"),
                List.of("纯咖啡", "苦"), 18));
        result.put("strong", new PreferenceRule(
                List.of("提神", "醒脑", "熬夜", "困", "浓郁", "醇厚"),
                List.of("清淡", "轻盈"), 18));
        result.put("light", new PreferenceRule(
                List.of("清淡", "轻盈", "轻一点", "淡一点", "不腻"),
                List.of("浓郁", "醇厚", "甜腻"), 18));
        result.put("healthy", new PreferenceRule(
                List.of("健康", "低卡", "低脂", "无糖", "低糖"),
                List.of("奶油", "高糖", "甜腻"), 18));
        return Collections.unmodifiableMap(result);
    }

    private record PreferenceRule(List<String> aliases, List<String> positiveTerms,
                                  List<String> negativeTerms, int weight) {
        private PreferenceRule(List<String> aliases, List<String> negativeTerms, int weight) {
            this(aliases, aliases, negativeTerms, weight);
        }

        private PreferenceRule(List<String> aliases, List<String> positiveTerms,
                               List<String> negativeTerms, int weight) {
            this.aliases = List.copyOf(aliases);
            this.positiveTerms = List.copyOf(positiveTerms);
            this.negativeTerms = List.copyOf(negativeTerms);
            this.weight = weight;
        }
    }
}
