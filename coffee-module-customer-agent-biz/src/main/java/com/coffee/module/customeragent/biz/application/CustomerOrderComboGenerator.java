package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

final class CustomerOrderComboGenerator {
    private static final Logger log = LoggerFactory.getLogger(CustomerOrderComboGenerator.class);

    private static final String SYSTEM_PROMPT = """
            你是咖啡馆点单组合生成器。根据给定的商品列表和用户需求，生成最优的点单组合方案。
            【强制规则】
            1. 严格使用给定的商品 code 来组合，不能编造不存在的商品。
            2. 如果用户需要 mix 组合（吃喝混合），每个方案必须同时包含食品(food/dessert)和饮品(coffee/tea/ice)。
            3. 每个方案的商品数量必须严格等于 target_count，不能多也不能少。
            4. 每个方案的总价格不能超过 budget_max。
            5. 必须包含用户指定的 must_include 商品。
            6. 排除 avoid 中提到的商品或品类。
            7. 输出 3 套不同的方案，每套方案商品尽量不同。
            8. 输出必须是合法 JSON，禁止 markdown，禁止额外文字。
            9. product_codes 数组中的 code 必须来自给定商品列表的 code 字段。
            10. 生成的 product_codes 数组长度必须精确等于 target_count。
            """;

    private final ZhipuChatClient chatClient;
    private final ObjectMapper jsonMapper;

    CustomerOrderComboGenerator(ZhipuChatClient chatClient, ObjectMapper jsonMapper) {
        this.chatClient = chatClient;
        this.jsonMapper = jsonMapper;
    }

    List<ComboPlan> generateCombos(List<MenuItemDTO> candidates,
                                    CustomerOrderIntentParser.Intent intent,
                                    double maxBudget) {
        if (candidates.isEmpty()) return List.of();

        String productListStr = formatProductList(candidates);
        String constraintStr = formatConstraints(intent, maxBudget);

        String userPrompt = "商品列表：\n" + productListStr + "\n\n" +
                "约束条件：\n" + constraintStr + "\n\n" +
                "请生成 3 套不同的组合方案，输出 JSON 格式：\n" +
                "{\n" +
                "  \"combos\": [\n" +
                "    {\n" +
                "      \"product_codes\": [\"CODE1\", \"CODE2\"],\n" +
                "      \"total_price\": 45.0,\n" +
                "      \"reasoning\": \"为什么这样搭配\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try {
            Optional<String> rawJson = chatClient.callJson(SYSTEM_PROMPT, userPrompt, 1200);
            if (rawJson.isEmpty()) {
                log.warn("LLM 组合生成返回为空");
                return List.of();
            }

            String jsonText = chatClient.extractJson(rawJson.get());
            log.debug("LLM 组合生成原始 JSON: {}", jsonText.length() > 800 ? jsonText.substring(0, 800) + "..." : jsonText);

            JsonNode root = jsonMapper.readTree(jsonText);
            List<ComboPlan> combos = parseCombos(root, candidates);
            log.info("LLM 生成 {} 套组合方案", combos.size());
            return combos;
        } catch (Exception e) {
            log.warn("LLM 组合生成失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String formatProductList(List<MenuItemDTO> candidates) {
        StringBuilder sb = new StringBuilder();
        for (MenuItemDTO p : candidates) {
            String category = safe(p.getCategoryCode());
            String categoryName = Map.of("coffee", "饮品", "tea", "茶饮", "ice", "冰饮",
                    "food", "轻食", "dessert", "甜点").getOrDefault(category, category);
            double price = p.getPriceMedium() != null ? p.getPriceMedium() :
                    p.getBasePrice() != null ? p.getBasePrice() : 0;
            sb.append(String.format(Locale.ROOT,
                    "  {\"code\":\"%s\", \"name\":\"%s\", \"category\":\"%s\", \"price\":%.1f, \"temperature\":\"%s\"}%n",
                    safe(p.getCode()), safe(p.getName()), categoryName, price, safe(p.getTemperature())));
        }
        return sb.toString();
    }

    private String formatConstraints(CustomerOrderIntentParser.Intent intent, double maxBudget) {
        StringBuilder sb = new StringBuilder();
        sb.append("需要的品类: ").append(intent.requiredCategories()).append("\n");
        sb.append("排除的品类: ").append(intent.excludedCategories()).append("\n");
        sb.append("排除的商品: ").append(intent.excludedProducts()).append("\n");
        sb.append("目标件数: ").append(intent.itemCount()).append("\n");
        sb.append("最大预算: ¥").append(maxBudget).append("\n");
        sb.append("偏好温度: ").append(intent.temperature()).append("\n");
        sb.append("需要 mix 组合: ").append(intent.pairing()).append("\n");
        return sb.toString();
    }

    private List<ComboPlan> parseCombos(JsonNode root, List<MenuItemDTO> candidates) {
        List<ComboPlan> result = new ArrayList<>();
        if (!root.has("combos") || !root.get("combos").isArray()) return result;

        Set<String> validCodes = candidates.stream()
                .map(MenuItemDTO::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, MenuItemDTO> codeToProduct = candidates.stream()
                .collect(Collectors.toMap(MenuItemDTO::getCode, p -> p, (a, b) -> a));

        for (JsonNode comboNode : root.get("combos")) {
            List<String> codes = new ArrayList<>();
            JsonNode codesNode = comboNode.get("product_codes");
            if (codesNode != null && codesNode.isArray()) {
                for (JsonNode c : codesNode) {
                    String code = c.asText();
                    if (validCodes.contains(code)) {
                        codes.add(code);
                    } else {
                        log.warn("LLM 生成了不存在的商品 code: {}", code);
                    }
                }
            }

            if (codes.isEmpty()) continue;

            double totalPrice = 0;
            List<MenuItemDTO> products = new ArrayList<>();
            for (String code : codes) {
                MenuItemDTO p = codeToProduct.get(code);
                if (p != null) {
                    products.add(p);
                    double price = p.getPriceMedium() != null ? p.getPriceMedium() :
                            p.getBasePrice() != null ? p.getBasePrice() : 0;
                    totalPrice += price;
                }
            }

            String reasoning = comboNode.has("reasoning") ? comboNode.get("reasoning").asText() : "";
            result.add(new ComboPlan(codes, totalPrice, reasoning, products));
        }

        return result;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    record ComboPlan(List<String> productCodes, double totalPrice, String reasoning,
                     List<MenuItemDTO> products) { }
}
