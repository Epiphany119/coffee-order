package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CustomerOrderLlmIntentParser {
    private static final Logger log = LoggerFactory.getLogger(CustomerOrderLlmIntentParser.class);
    private static final Pattern RAW_QUANTITY = Pattern.compile(
            "(\\d{1,2}|[一二三四五六七八九十两兩]+)\\s*(?:个|份|品|杯|碗|块|根|条|片|件)");

    private static final String SYSTEM_PROMPT = """
            你是咖啡馆点单意图解析器。将用户的自然语言需求解析为严格的 JSON 结构。
            【强制规则】
            1. target_count 必须精确统计用户想要的商品件数：
               - "一个A，一个B，两杯C" = 4 件（1+1+2）
               - "两个A一份B" = 3 件（2+1）
               - "三杯XX" = 3 件
               - 中文数量词映射：一/一个=1, 两/二/两个=2, 三/三个=3, 四/四个=4, 五/五个=5
            2. 用户同时提到食物和饮品时，expect_combination 必须为 "mix"，need_food=true 且 need_drink=true。
            3. 输出必须是合法 JSON，禁止 markdown 代码块，禁止额外解释文字。
            4. target_count 是用户希望商品总数量，识别不到时填 null。
            5. 品类判断：汉堡/薯条/三明治/热狗/鸡翅/小吃/披萨 → food；蛋糕/甜点/甜品 → dessert；咖啡/拿铁/美式 → coffee；冰沙/冰淇淋 → ice；茶饮/果茶 → tea。
            6. 当用户输入口语模糊，对应的字段填 null，不要臆造需求。
            7. temperature 字段：cold=冰/冷/冰镇；hot=热/暖/温的；any=不指定。
            8. must_include 放用户明确点名的每个商品（含数量，如"2杯冰沙"）；avoid 放明确不要的关键词。
            9. tags 数组放口味/偏好标签，如"冰饮"、"低糖"、"提神"、"甜点"等。
            10. 如果用户点了多种不同商品，must_include 必须包含每一种。
            """;

    private static final String JSON_SCHEMA = """
            {
              "target_count": null,
              "need_food": false,
              "need_drink": false,
              "budget_max": null,
              "tags": [],
              "must_include": [],
              "avoid": [],
              "expect_combination": "single",
              "temperature": "any",
              "user_intent_raw": ""
            }
            """;

    private final ZhipuChatClient chatClient;
    private final ObjectMapper jsonMapper;
    private final CustomerOrderIntentParser fallbackParser;

    CustomerOrderLlmIntentParser(ZhipuChatClient chatClient, ObjectMapper jsonMapper) {
        this.chatClient = chatClient;
        this.jsonMapper = jsonMapper;
        this.fallbackParser = new CustomerOrderIntentParser();
    }

    CustomerOrderIntentParser.Intent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return fallbackParser.parse("");
        }
        try {
            CustomerOrderIntentParser.Intent llmIntent = parseViaLlm(raw);
            if (llmIntent != null) {
                log.info("LLM 意图解析成功: required={}, itemCount={}, budget={}, pairing={}",
                        llmIntent.requiredCategories(), llmIntent.itemCount(),
                        llmIntent.budget(), llmIntent.pairing());
                return llmIntent;
            }
        } catch (Exception e) {
            log.warn("LLM 意图解析异常，降级到规则解析: {}", e.getMessage());
        }
        log.info("LLM 意图解析失败，降级到规则解析");
        return fallbackParser.parse(raw);
    }

    private CustomerOrderIntentParser.Intent parseViaLlm(String raw) {
        String userPrompt = "解析以下咖啡馆点单需求为 JSON 结构：\n\"" + raw + "\"\n\n" +
                "输出格式示例（严格遵循，字段可为 null 或空数组）：\n" + JSON_SCHEMA;

        Optional<String> rawJson = chatClient.callJson(SYSTEM_PROMPT, userPrompt, 500);
        if (rawJson.isEmpty()) return null;

        String jsonText = chatClient.extractJson(rawJson.get());
        log.debug("LLM 原始意图 JSON: {}", jsonText);

        try {
            JsonNode node = jsonMapper.readTree(jsonText);
            return mapToIntent(node, raw);
        } catch (Exception e) {
            log.warn("LLM 返回 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private CustomerOrderIntentParser.Intent mapToIntent(JsonNode node, String rawText) {
        if (node == null || !node.isObject()) return null;
        CustomerOrderIntentParser.Intent rawIntent = fallbackParser.parse(rawText);
        // 模型字段只能作为候选提示，必须先被原始用户文本验证，避免示例值或幻觉变成约束。
        List<String> groundedTags = groundedTerms(rawText, readStringArray(node, "tags"));
        List<String> mustInclude = groundedPositiveTerms(rawText, readStringArray(node, "must_include"));
        List<String> avoid = groundedNegatedTerms(rawText, readStringArray(node, "avoid"));
        List<String> tags = CustomerOrderIntentCatalog.canonicalPreferenceTags(rawText, groundedTags);

        // 品类由统一词典从原文提取；LLM 只能补充原文中确实出现的商品关键词。
        List<String> requiredCategories = new ArrayList<>(rawIntent.requiredCategories());
        Set<String> excludedCategories = new LinkedHashSet<>(rawIntent.excludedCategories());

        for (String category : CustomerOrderIntentCatalog.categoriesFromText(String.join(" ", mustInclude))) {
            if (!requiredCategories.contains(category)) requiredCategories.add(category);
        }

        // === 排除品类处理 ===
        for (String a : avoid) {
            // avoid 中的词已经经过原文否定校验；只有明确的泛化品类词才能排除整类，
            // “甜的饮品/冰的咖啡”仍按属性偏好处理，不能误杀整个品类。
            excludedCategories.addAll(CustomerOrderIntentCatalog.explicitlyExcludedCategoriesFromTerm(a));
        }

        requiredCategories.removeAll(excludedCategories);

        // === 数量词自动展开 ===
        // LLM 通常把"两杯冰沙"只解析为一个"冰沙"，需要在这里根据原始文本展开数量
        // 例如："两杯冰沙" → mustInclude 中添加 2 个 "冰沙"
        //      "三个汉堡" → mustInclude 中添加 3 个 "汉堡"
        mustInclude = expandQuantityFromRawText(rawText, mustInclude);

        log.info("数量词展开后 mustInclude: {}", mustInclude);

        // 数量、预算、温度和排除项以原始输入为准；模型不能把示例值变成业务约束。
        int rawQuantity = rawQuantityTotal(rawText);
        int itemCount = rawQuantity > 0
                ? Math.max(rawQuantity, mustInclude.size())
                : Math.max(Math.max(rawIntent.itemCount(), mustInclude.size()), 1);
        itemCount = Math.min(itemCount, 10);
        CustomerOrderIntentParser.Temperature temperature = rawIntent.temperature();
        Integer budgetMax = rawIntent.budget();
        boolean pairing = rawIntent.pairing() || requiredCategories.size() >= 2;

        Set<String> excludedProducts = new LinkedHashSet<>(rawIntent.excludedProducts());
        excludedProducts.addAll(avoid);

        String summary = buildSummary(requiredCategories, excludedCategories, excludedProducts,
                temperature, budgetMax, itemCount, tags);

        return new CustomerOrderIntentParser.Intent(
                List.copyOf(requiredCategories),
                excludedCategories,
                excludedProducts,
                temperature,
                budgetMax,
                pairing,
                itemCount,
                rawIntent.pricePreference(),
                summary,
                List.copyOf(mustInclude),
                tags
        );
    }

    private List<String> readStringArray(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node.get(fieldName)) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private List<String> groundedTerms(String rawText, List<String> values) {
        String normalizedRaw = compact(rawText);
        Set<String> grounded = new LinkedHashSet<>();
        for (String value : values) {
            String term = stripLeadingQuantity(value);
            String normalizedTerm = compact(term);
            if (normalizedTerm.isBlank() || normalizedTerm.length() > 40) continue;
            if (normalizedRaw.contains(normalizedTerm)) grounded.add(term.trim());
        }
        return List.copyOf(grounded);
    }

    private List<String> groundedNegatedTerms(String rawText, List<String> values) {
        return groundedTerms(rawText, values).stream()
                .filter(term -> isNegated(rawText, term))
                .toList();
    }

    private List<String> groundedPositiveTerms(String rawText, List<String> values) {
        return groundedTerms(rawText, values).stream()
                .filter(term -> hasPositiveOccurrence(rawText, term))
                .toList();
    }

    private boolean hasPositiveOccurrence(String rawText, String term) {
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        String normalizedTerm = stripLeadingQuantity(term).toLowerCase(Locale.ROOT).trim();
        if (normalizedTerm.isBlank()) return false;
        int from = 0;
        while (from < text.length()) {
            int index = text.indexOf(normalizedTerm, from);
            if (index < 0) return false;
            if (!CustomerOrderIntentCatalog.isNegatedAt(text, index)) return true;
            from = index + normalizedTerm.length();
        }
        return false;
    }

    private boolean isNegated(String rawText, String term) {
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        String normalizedTerm = stripLeadingQuantity(term).toLowerCase(Locale.ROOT).trim();
        if (normalizedTerm.isBlank()) return false;
        int from = text.indexOf(normalizedTerm);
        while (from >= 0) {
            if (CustomerOrderIntentCatalog.isNegatedAt(text, from)) {
                return true;
            }
            from = text.indexOf(normalizedTerm, from + normalizedTerm.length());
        }
        return false;
    }

    private String stripLeadingQuantity(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst(
                "^(?:\\d{1,2}|[一二三四五六七八九十两兩]+)\\s*(?:个|份|品|杯|碗|块|根|条|片|件)?", "");
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。.!！?？;；:：、\\\"“”'‘’]", "");
    }

    private int rawQuantityTotal(String rawText) {
        Matcher matcher = RAW_QUANTITY.matcher(rawText == null ? "" : rawText);
        int total = 0;
        while (matcher.find()) {
            if (CustomerOrderIntentCatalog.isNegatedAt(rawText, matcher.start())) continue;
            int count = parseQuantity(matcher.group(1));
            if (count > 0) total = Math.min(10, total + count);
        }
        return total;
    }

    private int parseQuantity(String value) {
        if (value == null || value.isBlank()) return 0;
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Math.min(10, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if ("两".equals(value) || "兩".equals(value) || "二".equals(value)) return 2;
        if ("十".equals(value)) return 10;
        if (value.length() == 1) {
            return "一二三四五六七八九十".indexOf(value) + 1;
        }
        return 0;
    }

    /**
     * 根据原始用户输入中的数量词展开 mustInclude 列表
     *
     * 例如：
     * - "两杯冰沙" → mustInclude 中 "冰沙" 出现 2 次
     * - "三个汉堡" → mustInclude 中 "汉堡" 出现 3 次
     * - "两份薯条，三杯咖啡" → mustInclude 中 "薯条" 2 次、"咖啡" 3 次
     *
     * 中文数量词映射：
     * - 一/一个 = 1, 两/二/两个 = 2, 三/三个 = 3, 四/四个 = 4, 五/五个 = 5
     */
    private List<String> expandQuantityFromRawText(String rawText, List<String> mustInclude) {
        if (rawText == null || rawText.isBlank() || mustInclude.isEmpty()) {
            return mustInclude;
        }

        List<String> expanded = new ArrayList<>(mustInclude);
        String text = rawText.toLowerCase(Locale.ROOT);

        // 中文数量词映射（key=数量词+量词, value=数量）
        Map<String, Integer> chineseNumbers = new HashMap<>();
        chineseNumbers.put("一", 1);
        chineseNumbers.put("一个", 1);
        chineseNumbers.put("一杯", 1);
        chineseNumbers.put("一份", 1);
        chineseNumbers.put("两", 2);
        chineseNumbers.put("二", 2);
        chineseNumbers.put("两个", 2);
        chineseNumbers.put("两杯", 2);
        chineseNumbers.put("两份", 2);
        chineseNumbers.put("三", 3);
        chineseNumbers.put("三个", 3);
        chineseNumbers.put("三杯", 3);
        chineseNumbers.put("三份", 3);
        chineseNumbers.put("四", 4);
        chineseNumbers.put("四个", 4);
        chineseNumbers.put("四杯", 4);
        chineseNumbers.put("四份", 4);
        chineseNumbers.put("五", 5);
        chineseNumbers.put("五个", 5);
        chineseNumbers.put("五杯", 5);
        chineseNumbers.put("五份", 5);
        chineseNumbers.put("十", 10);

        // 遍历 mustInclude 中的每个商品名，检查原始文本中是否有 "数量词 + 商品名" 模式
        for (String itemName : mustInclude) {
            if (itemName == null || itemName.isBlank()) continue;

            // 查找原始文本中 "X + itemName" 的模式，X 是数量词
            for (Map.Entry<String, Integer> entry : chineseNumbers.entrySet()) {
                String quantifier = entry.getKey();
                int count = entry.getValue();

                // 检查 "X + itemName" 或 "X + 量词 + itemName" 模式
                // 例如："两杯冰沙" → "两" + "杯" + "冰沙"
                //       "三个汉堡" → "三" + "个" + "汉堡"
                String pattern1 = quantifier + itemName;       // "两冰沙" (不常见但可能)
                String pattern2 = quantifier + "杯" + itemName; // "两杯冰沙"
                String pattern3 = quantifier + "个" + itemName; // "三个汉堡"
                String pattern4 = quantifier + "份" + itemName; // "两份薯条"

                if (text.contains(pattern2) || text.contains(pattern3) || text.contains(pattern4) ||
                    (text.contains(pattern1) && !quantifier.equals("一"))) {

                    // 检查 expanded 中该商品名的出现次数
                    long currentCount = expanded.stream().filter(itemName::equals).count();

                    // 如果当前次数少于目标数量，补充
                    if (currentCount < count) {
                        int toAdd = (int) (count - currentCount);
                        for (int i = 0; i < toAdd; i++) {
                            expanded.add(itemName);
                        }
                        log.info("数量展开: 「{}」 从 {} → {} (原始文本: {})",
                                itemName, currentCount, count, rawText);
                    }
                    break; // 已找到匹配的数量词，不再继续检查其他数量词
                }
            }
        }

        return expanded;
    }

    private String buildSummary(List<String> required, Set<String> excluded,
                                 Set<String> excludedProducts,
                                 CustomerOrderIntentParser.Temperature temperature,
                                 Integer budget, int itemCount, List<String> tags) {
        List<String> parts = new ArrayList<>();
        if (!required.isEmpty()) {
            parts.add("指定" + required.stream().map(this::catName)
                    .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        if (!excluded.isEmpty()) {
            parts.add("排除" + excluded.stream().map(this::catName)
                    .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        if (!excludedProducts.isEmpty()) {
            parts.add("不要" + String.join("、", excludedProducts));
        }
        if (temperature == CustomerOrderIntentParser.Temperature.COLD) parts.add("偏好冰饮");
        if (temperature == CustomerOrderIntentParser.Temperature.HOT) parts.add("偏好热饮");
        if (budget != null) parts.add("预算不高于 ¥" + budget);
        if (itemCount > 0) parts.add("指定" + itemCount + "件商品");
        if (!tags.isEmpty()) parts.add("偏好标签: " + String.join("、", tags));
        return parts.isEmpty() ? "未指定品类，按偏好与门店数据推荐" : String.join("；", parts);
    }

    private String catName(String category) {
        return CustomerOrderIntentCatalog.categoryName(category);
    }
}
