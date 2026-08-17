package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 商品选择服务 — 直接传入候选集，让 LLM 选择最优组合
 *
 * 简化架构：
 * 1. 上游 CandidateSetService 已经完成 RAG + MySQL 交集查询
 * 2. 本服务只负责：给 LLM 候选集 → 让 LLM 选择 → 验证结果
 * 3. 如果 LLM 选择不对，引导重试一次
 *
 * ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 * │ CandidateSet │──► │ 本服务       │──► │ LLM 选择结果 │
 * │ RAG + MySQL  │    │ 给候选集     │    │ product_codes│
 * └──────────────┘    └──────────────┘    └──────────────┘
 */
@Component
public class LlmToolOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(LlmToolOrchestrator.class);

    private final ZhipuChatClient chatClient;
    private final ObjectMapper jsonMapper;

    public LlmToolOrchestrator(ZhipuChatClient chatClient, ObjectMapper jsonMapper) {
        this.chatClient = chatClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 基于候选集的 LLM 组合选择
     *
     * @param storeId     门店 ID
     * @param userQuery   用户原始输入
     * @param intent      解析后的意图（结构化）
     * @param candidates  已筛选好的候选集（来自 CandidateSetService）
     * @return 选择的商品 code 列表 + LLM 推理过程
     */
    public ToolResult selectFromCandidates(Long storeId, String userQuery,
                                            CustomerOrderIntentParser.Intent intent,
                                            CandidateSetService.CandidateResult candidates) {
        int itemCount = intent.itemCount() > 0 ? intent.itemCount() : 3;
        log.info("=== LLM 商品选择开始 ===");
        log.info("意图: categories={}, count={}, budget={}, 候选集大小={}",
                intent.requiredCategories(), itemCount, intent.budget(), candidates.size());

        // 候选集不足，直接降级
        if (candidates.size() < 2) {
            log.warn("候选集数量不足 ({})，直接降级", candidates.size());
            return degradeFromCandidates(candidates, itemCount);
        }

        // Step 1: 构造提示词
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildSelectionPrompt(userQuery, intent, candidates, itemCount);

        // Step 2: 调用 LLM 选择组合
        long start = System.currentTimeMillis();
        Optional<String> response = chatClient.callJson(systemPrompt, userPrompt, 1500);
        long elapsed = System.currentTimeMillis() - start;
        log.info("LLM 选择耗时: {}ms", elapsed);

        if (response.isEmpty()) {
            log.warn("LLM 调用返回为空，使用规则降级");
            return degradeFromCandidates(candidates, itemCount);
        }

        String json = chatClient.extractJson(response.get());
        log.info("LLM 选择响应: {}", json.length() > 300 ? json.substring(0, 300) + "..." : json);

        // Step 3: 解析选择结果
        ToolResult result = parseSelectionResult(json, candidates);

        // Step 4: 如果数量不对，引导重试
        if (result.productCodes().size() != itemCount && elapsed < 20000) {
            log.info("LLM 选择数量 {} 与期望 {} 不符，引导重试", result.productCodes().size(), itemCount);
            result = retryWithGuidance(userPrompt, systemPrompt, candidates, intent, itemCount);
        }

        log.info("最终选择结果: codes={}, reasoning={}", result.productCodes(), result.reasoning());
        return result;
    }

    /**
     * 引导式重试：告诉 LLM 数量不对，需要重新选择
     */
    private ToolResult retryWithGuidance(String userPrompt, String systemPrompt,
                                          CandidateSetService.CandidateResult candidates,
                                          CustomerOrderIntentParser.Intent intent,
                                          int itemCount) {
        String retryPrompt = buildRetryPrompt(userPrompt, intent, candidates, itemCount);

        Optional<String> retryResponse = chatClient.callJson(systemPrompt, retryPrompt, 1500);
        if (retryResponse.isPresent()) {
            String retryJson = chatClient.extractJson(retryResponse.get());
            ToolResult retryResult = parseSelectionResult(retryJson, candidates);
            if (retryResult.productCodes().size() == itemCount) {
                return retryResult;
            }
            log.info("重试仍未达标 ({} vs {})，降级为候选集 Top", retryResult.productCodes().size(), itemCount);
        }

        return degradeFromCandidates(candidates, itemCount);
    }

    /**
     * 规则降级：直接从候选集中按分数取前 N 个
     */
    private ToolResult degradeFromCandidates(CandidateSetService.CandidateResult candidates, int itemCount) {
        List<String> codes = candidates.candidates().stream()
                .limit(itemCount)
                .map(MenuItemDTO::getCode)
                .collect(Collectors.toList());
        log.warn("降级选择 {} 个商品: {}", codes.size(), codes);
        return new ToolResult("degraded", codes, "规则降级：按语义分数选择");
    }

    // === 提示词构建 ===

    private String buildSystemPrompt() {
        return """
                你是咖啡馆点单推荐助手。从提供的候选商品列表中，为用户选择最合适的组合。

                【选择原则】
                1. 严格按照用户需求的品类选择（如用户要冰饮就选 ice 品类）
                2. 严格按照用户要求的件数选择，不能多也不能少
                3. 如果有预算限制，总价格不能超过预算
                4. 优先选择语义分数高的商品

                【输出格式】
                {
                  "product_codes": ["CODE1", "CODE2", "CODE3"],
                  "reasoning": "选择理由"
                }
                """;
    }

    private String buildSelectionPrompt(String userQuery,
                                         CustomerOrderIntentParser.Intent intent,
                                         CandidateSetService.CandidateResult candidates,
                                         int itemCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求：\"").append(userQuery).append("\"\n\n");

        // 意图约束
        sb.append("【需求约束】\n");
        sb.append("  目标品类: ").append(intent.requiredCategories()).append("\n");
        sb.append("  目标件数: ").append(itemCount).append("\n");
        sb.append("  最大预算: ¥").append(intent.budget() != null ? intent.budget() : "不限").append("\n");
        sb.append("  温度偏好: ").append(intent.temperature()).append("\n");
        if (!intent.excludedCategories().isEmpty())
            sb.append("  排除品类: ").append(intent.excludedCategories()).append("\n");
        if (!intent.excludedProducts().isEmpty())
            sb.append("  排除商品: ").append(intent.excludedProducts()).append("\n");

        // 候选商品列表
        sb.append("\n【候选商品列表】\n");
        int limit = Math.min(candidates.size(), 40);
        for (int i = 0; i < limit; i++) {
            MenuItemDTO p = candidates.candidates().get(i);
            double score = candidates.semanticScores().getOrDefault(p.getCode(), 0.0);
            sb.append(String.format(Locale.ROOT,
                    "  [%d] code=%s, name=%s, category=%s, temp=%s, score=%.3f%n",
                    i + 1, safe(p.getCode()), safe(p.getName()), safe(p.getCategoryCode()),
                    safe(p.getTemperature()), score));
        }

        sb.append(String.format("\n请从候选商品中选择 ** exactly %d 件 ** 最合适的组合，输出 JSON：\n", itemCount));
        sb.append("{\n");
        sb.append("  \"product_codes\": [\"CODE1\", \"CODE2\"],\n");
        sb.append("  \"reasoning\": \"选择理由\"\n");
        sb.append("}\n");
        sb.append("\n【重要】product_codes 必须使用候选列表中的 code 字段，数量必须严格等于 " + itemCount + " 件。");

        return sb.toString();
    }

    private String buildRetryPrompt(String userQuery,
                                     CustomerOrderIntentParser.Intent intent,
                                     CandidateSetService.CandidateResult candidates,
                                     int itemCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求：\"").append(userQuery).append("\"\n\n");
        sb.append("上一次选择的商品数量不对，需要 ** exactly ").append(itemCount).append(" 件 **。\n\n");

        sb.append("【需求约束】\n");
        sb.append("  目标品类: ").append(intent.requiredCategories()).append("\n");
        sb.append("  目标件数: ").append(itemCount).append("\n");
        sb.append("  最大预算: ¥").append(intent.budget() != null ? intent.budget() : "不限").append("\n");

        sb.append("\n【候选商品列表】\n");
        int limit = Math.min(candidates.size(), 40);
        for (int i = 0; i < limit; i++) {
            MenuItemDTO p = candidates.candidates().get(i);
            double score = candidates.semanticScores().getOrDefault(p.getCode(), 0.0);
            sb.append(String.format(Locale.ROOT,
                    "  [%d] code=%s, name=%s, category=%s, score=%.3f%n",
                    i + 1, safe(p.getCode()), safe(p.getName()), safe(p.getCategoryCode()), score));
        }

        sb.append(String.format("\n请严格选择 %d 件商品，输出 JSON：\n", itemCount));
        sb.append("{\n");
        sb.append("  \"product_codes\": [\"CODE1\", \"CODE2\"],\n");
        sb.append("  \"reasoning\": \"选择理由\"\n");
        sb.append("}");

        return sb.toString();
    }

    // === 结果解析 ===

    private ToolResult parseSelectionResult(String jsonText,
                                             CandidateSetService.CandidateResult candidates) {
        try {
            JsonNode root = jsonMapper.readTree(jsonText);
            List<String> codes = new ArrayList<>();
            JsonNode codesNode = root.get("product_codes");
            if (codesNode != null && codesNode.isArray()) {
                for (JsonNode c : codesNode) {
                    String code = c.asText();
                    if (candidates.candidates().stream().anyMatch(p -> code.equals(p.getCode()))) {
                        codes.add(code);
                    } else {
                        log.warn("LLM 选择了候选集外的商品: {}", code);
                    }
                }
            }
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "";
            return new ToolResult("ok", codes, reasoning);
        } catch (Exception e) {
            log.warn("解析选择结果失败: {}", e.getMessage());
            return degradeFromCandidates(candidates, 3);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    public record ToolResult(String status, List<String> productCodes, String reasoning) {
        public static ToolResult degraded(String reason, List<String> codes) {
            return new ToolResult("degraded", codes, reason);
        }
    }
}
