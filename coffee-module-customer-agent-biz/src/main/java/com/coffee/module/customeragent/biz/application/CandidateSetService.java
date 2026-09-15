package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 候选集服务：精确匹配、MySQL 分类和 RAG 语义结果合并去重，形成候选集。
 *
 * 架构思想：
 * ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
 * │ RAG 语义检索 │     │ MySQL 分类  │     │ 合并去重   │
 * │ (向量相似度) │ ∪   │ (结构化查询) │ ──► │  Candidate  │
 * └─────────────┘     └─────────────┘     │    Set      │
 *                                          └──────┬──────┘
 *                                                 │
 *                                                 ▼
 * ┌──────────────────────────────────────────────────────────┐
 * │  LLM 从候选集中挑选组合（不生成新商品，只做选择）         │
 * └──────────────────────────────────────────────────────────┘
 */
@Component
public class CandidateSetService {
    private static final Logger log = LoggerFactory.getLogger(CandidateSetService.class);

    private final MenuService menuService;
    private final CustomerSemanticMenuRetriever semanticRetriever;

    public CandidateSetService(MenuService menuService,
                                CustomerSemanticMenuRetriever semanticRetriever) {
        this.menuService = menuService;
        this.semanticRetriever = semanticRetriever;
    }

    /**
     * 获取候选集：精确匹配 ∪ MySQL 分类 ∪ RAG 语义。
     *
     * 优先级策略（从高到低）：
     * 1. 精确商品名匹配（mustInclude 关键词命中商品名） → 强制包含
     * 2. MySQL 分类命中的商品 → 高优先级
     * 3. RAG 语义检索命中 → 补充
     *
     * @param storeId      门店 ID
     * @param userQuery    用户原始输入
     * @param categories   需要的品类列表
     * @param allProducts  全量商品
     * @param itemCount    需要的商品数量
     * @param mustInclude  用户明确点名的商品关键词
     */
    public CandidateResult getCandidates(Long storeId, String userQuery,
                                          List<String> categories,
                                          List<MenuItemDTO> allProducts,
                                          Integer itemCount,
                                          List<String> mustInclude) {
        log.info("=== 候选集查询开始 ===");
        log.info("用户输入: {}", userQuery);
        log.info("目标品类: {}, 目标数量: {}, mustInclude: {}", categories, itemCount, mustInclude);

        // Step 0: 精确商品名匹配（最高优先级）
        // 用户明确点名的商品（如"汉堡"、"薯条"）必须出现在候选集中
        Set<String> exactMatchCodes = new LinkedHashSet<>();
        List<MenuItemDTO> exactMatches = new ArrayList<>();
        if (mustInclude != null && !mustInclude.isEmpty()) {
            for (String keyword : mustInclude) {
                if (keyword == null || keyword.isBlank()) continue;
                List<MenuItemDTO> matched = allProducts.stream()
                        .filter(p -> p.getName() != null && p.getName().contains(keyword))
                        .filter(p -> !exactMatchCodes.contains(p.getCode()))
                        .toList();
                for (MenuItemDTO p : matched) {
                    exactMatchCodes.add(p.getCode());
                    exactMatches.add(p);
                }
                if (!matched.isEmpty()) {
                    log.info("精确匹配「{}」→ {} 个商品: {}", keyword, matched.size(),
                            matched.stream().map(MenuItemDTO::getName).collect(Collectors.joining(", ")));
                }
            }
        }

        // Step 1: RAG 语义检索（向量相似度）
        Map<String, Double> raScores = semanticRetriever.retrieve(storeId, userQuery, allProducts);
        log.info("RAG 检索命中 {} 个商品，Top5: {}",
                raScores.size(),
                raScores.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(5)
                        .map(e -> e.getKey() + "=" + String.format("%.3f", e.getValue()))
                        .collect(Collectors.joining(", ")));

        // Step 2: MySQL 分类查询（结构化品类匹配）
        Set<String> mysqlMatchedCodes = new LinkedHashSet<>();
        if (categories != null && !categories.isEmpty()) {
            for (String cat : categories) {
                allProducts.stream()
                        .filter(p -> matchesCategoryStrict(p, cat))
                        .forEach(p -> mysqlMatchedCodes.add(p.getCode()));
            }
        } else {
            allProducts.forEach(p -> mysqlMatchedCodes.add(p.getCode()));
        }

        log.info("MySQL 分类查询命中 {} 个商品", mysqlMatchedCodes.size());

        // Step 3: 组合候选集（精确匹配优先 → 分类匹配 → RAG 补充）
        List<MenuItemDTO> candidates = new ArrayList<>();
        Set<String> addedCodes = new LinkedHashSet<>();

        // 3a. 精确匹配的商品（强制包含）
        for (MenuItemDTO p : exactMatches) {
            if (addedCodes.add(p.getCode())) {
                candidates.add(p);
            }
        }
        log.info("精确匹配加入 {} 个商品", candidates.size());

        // 3b. MySQL 分类命中的商品（排除已加入的），按 RAG 分数降序
        List<MenuItemDTO> mysqlOnly = allProducts.stream()
                .filter(p -> mysqlMatchedCodes.contains(p.getCode()))
                .filter(p -> !addedCodes.contains(p.getCode()))
                .sorted(semanticComparator(raScores))
                .collect(Collectors.toList());

        for (MenuItemDTO p : mysqlOnly) {
            if (addedCodes.add(p.getCode())) {
                candidates.add(p);
            }
        }
        log.info("MySQL 分类补充 {} 个商品，总计 {} 个", mysqlOnly.size(), candidates.size());

        // 3c. 数量不足时，用 RAG Top-N 补充
        if (itemCount != null && candidates.size() < itemCount) {
            int needMore = itemCount - candidates.size();
            log.info("候选不足 {} 个，用 RAG 补充", needMore);

            List<MenuItemDTO> raOnly = allProducts.stream()
                    .filter(p -> !addedCodes.contains(p.getCode()))
                    .sorted(semanticComparator(raScores))
                    .limit(needMore * 3L)
                    .collect(Collectors.toList());

            for (MenuItemDTO p : raOnly) {
                if (addedCodes.add(p.getCode())) {
                    candidates.add(p);
                    if (itemCount != null && candidates.size() >= itemCount * 2) break;
                }
            }
        }

        // 3d. 完全无命中时退化为纯 RAG
        if (candidates.isEmpty()) {
            log.info("无任何命中，退化为纯 RAG 结果");
            candidates = allProducts.stream()
                    .sorted(semanticComparator(raScores))
                    .limit(itemCount != null ? itemCount * 3L : 30L)
                    .collect(Collectors.toList());
        }

        // 打印 Top 候选
        log.info("最终候选集 {} 个，Top 8: {}",
                candidates.size(),
                candidates.stream().limit(8)
                        .map(p -> p.getName() + "(" + p.getCategoryCode() + ") " +
                                String.format("%.3f", raScores.getOrDefault(p.getCode(), 0.0)))
                        .collect(Collectors.joining(", ")));

        Set<String> raMatchedCodes = raScores.keySet();
        return new CandidateResult(candidates, raScores, exactMatchCodes, raMatchedCodes, mysqlMatchedCodes);
    }

    /** 兼容旧签名 — 不传 mustInclude 和 itemCount */
    public CandidateResult getCandidates(Long storeId, String userQuery,
                                          List<String> categories,
                                          List<MenuItemDTO> allProducts) {
        return getCandidates(storeId, userQuery, categories, allProducts, null, List.of());
    }

    /** 兼容旧签名 — 传 itemCount 但不传 mustInclude */
    public CandidateResult getCandidates(Long storeId, String userQuery,
                                          List<String> categories,
                                          List<MenuItemDTO> allProducts,
                                          Integer itemCount) {
        return getCandidates(storeId, userQuery, categories, allProducts, itemCount, List.of());
    }

    /** 品类是硬约束：候选集不把 dessert 自动当作 food，也不跨饮品品类替代。 */
    private boolean matchesCategoryStrict(MenuItemDTO product, String category) {
        return CustomerOrderIntentCatalog.matchesCategory(product, category);
    }

    private Comparator<MenuItemDTO> semanticComparator(Map<String, Double> semanticScores) {
        return Comparator.comparingDouble((MenuItemDTO p) ->
                        semanticScores.getOrDefault(p.getCode(), 0.0))
                .reversed()
                .thenComparing(Comparator.comparing(MenuItemDTO::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .thenComparing(p -> safe(p.getCode()))
                .thenComparing(p -> safe(p.getName()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 候选集查询结果 */
    public record CandidateResult(
            List<MenuItemDTO> candidates,
            Map<String, Double> semanticScores,
            Set<String> exactMatchCodes,
            Set<String> ragCodes,
            Set<String> mysqlCodes
    ) {
        public CandidateResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            semanticScores = semanticScores == null ? Map.of() : Map.copyOf(semanticScores);
            exactMatchCodes = exactMatchCodes == null ? Set.of() : Set.copyOf(exactMatchCodes);
            ragCodes = ragCodes == null ? Set.of() : Set.copyOf(ragCodes);
            mysqlCodes = mysqlCodes == null ? Set.of() : Set.copyOf(mysqlCodes);
        }

        public int size() {
            return candidates.size();
        }

        public MenuItemDTO getByCode(String code) {
            return candidates.stream()
                    .filter(p -> code.equals(p.getCode()))
                    .findFirst()
                    .orElse(null);
        }

        /** 将候选集收敛到最终硬约束过滤结果，模型只能看到这份集合。 */
        public CandidateResult restrictTo(List<MenuItemDTO> allowed) {
            if (allowed == null || allowed.isEmpty()) {
                return new CandidateResult(List.of(), Map.of(), Set.of(), Set.of(), Set.of());
            }
            Set<String> allowedCodes = allowed.stream()
                    .map(MenuItemDTO::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> candidateCodes = candidates.stream()
                    .map(MenuItemDTO::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            // 按上游传入的综合排序保留顺序；旧实现重新按 CandidateSet 原始顺序过滤，
            // 会把规则排序结果悄悄改回数据库/RAG 的顺序。
            List<MenuItemDTO> restricted = allowed.stream()
                    .filter(p -> p != null && allowedCodes.contains(p.getCode())
                            && candidateCodes.contains(p.getCode()))
                    .toList();
            if (restricted.isEmpty()) restricted = List.copyOf(allowed);
            Map<String, Double> restrictedScores = semanticScores.entrySet().stream()
                    .filter(entry -> allowedCodes.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (left, right) -> left, LinkedHashMap::new));
            return new CandidateResult(restricted, restrictedScores,
                    exactMatchCodes.stream().filter(allowedCodes::contains).collect(Collectors.toCollection(LinkedHashSet::new)),
                    ragCodes.stream().filter(allowedCodes::contains).collect(Collectors.toCollection(LinkedHashSet::new)),
                    mysqlCodes.stream().filter(allowedCodes::contains).collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        /** 兼容旧调用方；该字段实际表示精确商品名匹配结果。 */
        @Deprecated
        public Set<String> intersectionCodes() {
            return exactMatchCodes;
        }
    }
}
