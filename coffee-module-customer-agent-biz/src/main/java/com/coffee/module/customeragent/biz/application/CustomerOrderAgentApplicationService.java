package com.coffee.module.customeragent.biz.application;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.util.MoneyUtils;
import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.module.customeragent.api.CustomerOrderAgentService;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.order.api.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 顾客 Agent 应用层：LLM 结构化解析 + 硬约束校验 + 降级兜底。 */
@Service
public class CustomerOrderAgentApplicationService implements CustomerOrderAgentService {
    private static final Logger log = LoggerFactory.getLogger(CustomerOrderAgentApplicationService.class);

    private final MenuService menuService;
    private final FavoriteService favoriteService;
    private final OrderService orderService;
    private final JdbcTemplate jdbcTemplate;
    private final ZhipuChatClient chatClient;
    private final CustomerAgentPlanRegistry planRegistry;
    private final CustomerSemanticMenuRetriever semanticRetriever;
    private final MenuPriceSelectionTool priceSelectionTool;
    private final String templateStoreCode;
    private final CustomerOrderIntentParser fallbackIntentParser;
    private final CustomerOrderLlmIntentParser llmIntentParser;
    private final CustomerOrderComboGenerator comboGenerator;
    private final IntentValidator intentValidator;
    private final CandidateSetService candidateSetService;
    private final LlmToolOrchestrator toolOrchestrator;

    public CustomerOrderAgentApplicationService(MenuService menuService,
                                                FavoriteService favoriteService,
                                                OrderService orderService,
                                                JdbcTemplate jdbcTemplate,
                                                ZhipuChatClient chatClient,
                                                CustomerAgentPlanRegistry planRegistry,
                                                CustomerSemanticMenuRetriever semanticRetriever,
                                                MenuPriceSelectionTool priceSelectionTool,
                                                ObjectMapper objectMapper,
                                                CandidateSetService candidateSetService,
                                                LlmToolOrchestrator toolOrchestrator,
                                                @Value("${coffee.ai.template-store-code:jingan}") String templateStoreCode) {
        this.menuService = menuService;
        this.favoriteService = favoriteService;
        this.orderService = orderService;
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = chatClient;
        this.planRegistry = planRegistry;
        this.semanticRetriever = semanticRetriever;
        this.priceSelectionTool = priceSelectionTool;
        this.templateStoreCode = templateStoreCode;
        this.fallbackIntentParser = new CustomerOrderIntentParser();
        this.llmIntentParser = new CustomerOrderLlmIntentParser(chatClient, objectMapper);
        this.comboGenerator = new CustomerOrderComboGenerator(chatClient, objectMapper);
        this.intentValidator = new IntentValidator();
        this.candidateSetService = candidateSetService;
        this.toolOrchestrator = toolOrchestrator;
    }

    @Override
    public Map<String, Object> plan(Long storeId, Long userId, String guestId, String message) {
        if (message == null || message.trim().isEmpty())
            throw new ServiceException(400, "告诉我你的口味、预算或饮用场景吧");
        if (message.trim().length() > 300)
            throw new ServiceException(400, "点单需求不能超过 300 字");

        List<MenuItemDTO> products = menuService.getAllProducts(storeId);
        if (products.isEmpty())
            throw new ServiceException(404, "当前店铺暂无可点商品");

        String text = message.trim().toLowerCase(Locale.ROOT);

        // 第一层：LLM 结构化意图解析（失败时降级到规则解析）
        CustomerOrderIntentParser.Intent intent = llmIntentParser.parse(text);
        log.info("意图解析结果: requiredCategories={}, itemCount={}, budget={}, pairing={}, temperature={}",
                intent.requiredCategories(), intent.itemCount(), intent.budget(),
                intent.pairing(), intent.temperature());

        List<MenuItemDTO> favorites = favoriteService.getFavorites(userId, guestId);
        Set<String> favCodes = favorites.stream().map(MenuItemDTO::getCode).collect(Collectors.toSet());
        Set<String> favCats = favorites.stream().map(MenuItemDTO::getCategoryCode).collect(Collectors.toSet());
        Set<String> hotNames = orderService.getHotProducts(storeId).stream()
                .map(r -> String.valueOf(r.getOrDefault("name", ""))).collect(Collectors.toSet());
        Map<Long, Double> ratings = ratings(products);

        Map<String, Double> semanticScores = semanticRetriever.retrieve(storeId, message.trim(), products);

        // 硬约束过滤候选商品（规则引擎路径）
        List<MenuItemDTO> ruleFilteredCandidates = products.stream()
                .filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())))
                .filter(p -> !matchesExcludedProductOrCategory(p, intent.excludedProducts(), intent.excludedCategories()))
                .filter(p -> intent.itemCount() < 3 || !isFillerOnly(p))
                .filter(p -> intent.requiredCategories().isEmpty() ||
                        intent.requiredCategories().stream().anyMatch(category -> matchesCategory(p, category)))
                .filter(p -> matchesTemperature(p, intent.temperature()))
                .toList();

        if (ruleFilteredCandidates.isEmpty())
            throw new ServiceException(404, "当前门店没有同时满足「" + intent.summary() + "」的商品；可以放宽预算或让我推荐");

        // 第二层：工具驱动的组合生成（Tool-use + Steering + RAG ∩ MySQL）
        List<MenuItemDTO> selectedProducts = null;
        String engine = "FIKA Customer Agent · rule-tools";
        List<Map<String, Object>> options = new ArrayList<>();

        int targetItemCount = intent.itemCount() > 0 ? intent.itemCount() : 3;

        // 诊断日志：打印 intent 的所有关键字段
        log.info("=== 意图诊断 ===");
        log.info("原始输入: {}", message);
        log.info("requiredCategories: {}", intent.requiredCategories());
        log.info("explicitProductNames: {}", intent.explicitProductNames());
        log.info("itemCount: {}, budget: {}", targetItemCount, intent.budget());
        log.info("excludedCategories: {}", intent.excludedCategories());
        log.info("excludedProducts: {}", intent.excludedProducts());
        log.info("=================");

        // 2a: 先用 CandidateSetService 获取精确匹配 ∪ MySQL ∪ RAG 候选集
        CandidateSetService.CandidateResult candidateResult = candidateSetService.getCandidates(
                storeId, message.trim(), intent.requiredCategories(), products, targetItemCount,
                intent.explicitProductNames());
        List<MenuItemDTO> rawCandidates = new ArrayList<>(candidateResult.candidates());

        // 如果候选集为空，降级使用规则引擎过滤结果
        if (rawCandidates.isEmpty()) {
            log.info("CandidateSet 无结果，降级使用 ruleFilteredCandidates");
            rawCandidates.addAll(ruleFilteredCandidates);
        }

        log.info("候选集大小: {}, 精确匹配: {}, MySQL 匹配: {}",
                rawCandidates.size(),
                candidateResult.intersectionCodes().size(),
                candidateResult.mysqlCodes().size());

        // 应用额外过滤（排除项、温度、凑单过滤）
        final int finalTargetCount = targetItemCount;
        List<MenuItemDTO> candidates = rawCandidates.stream()
                .filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())))
                .filter(p -> !matchesExcludedProductOrCategory(p, intent.excludedProducts(), intent.excludedCategories()))
                .filter(p -> finalTargetCount < 3 || !isFillerOnly(p))
                .filter(p -> matchesTemperature(p, intent.temperature()))
                .toList();

        if (candidates.isEmpty())
            throw new ServiceException(404, "当前门店没有同时满足「" + intent.summary() + "」的商品；可以放宽预算或让我推荐");

        // 2b: 使用 LLM 选择器从候选集中选择组合
        if (!candidates.isEmpty()) {
            LlmToolOrchestrator.ToolResult toolResult = toolOrchestrator.selectFromCandidates(
                    storeId, message.trim(), intent, candidateResult);

            log.info("LLM 选择结果: status={}, codes={}, reasoning={}",
                    toolResult.status(), toolResult.productCodes(), toolResult.reasoning());
            log.info("候选集商品: {}", candidates.stream()
                    .map(p -> p.getName() + "(" + p.getCode() + ")")
                    .collect(Collectors.joining(", ")));

            if (!toolResult.productCodes().isEmpty()) {
                List<MenuItemDTO> toolSelected = toolResult.productCodes().stream()
                        .map(code -> candidates.stream()
                                .filter(p -> code.equals(p.getCode()))
                                .findFirst().orElse(null))
                        .filter(Objects::nonNull)
                        .toList();

                log.info("LLM 选中商品: {}", toolSelected.stream()
                        .map(MenuItemDTO::getName).collect(Collectors.joining(", ")));
                log.info("期望数量: {}, 实际选中: {}", targetItemCount, toolSelected.size());

                if (!toolSelected.isEmpty() && toolSelected.size() == targetItemCount) {
                    // 硬约束校验（传递精确匹配商品名用于豁免品类校验）
                    double totalPrice = toolSelected.stream()
                            .mapToDouble(p -> minimumPrice(storeId, p)).sum();
                    IntentValidator.ValidationResult validation = intentValidator.validateCombo(
                            toolSelected, intent, totalPrice, intent.explicitProductNames());

                    if (validation.valid()) {
                        selectedProducts = toolSelected;
                        engine = "FIKA Customer Agent · llm-select(" + toolResult.status() + ")";
                        log.info("LLM 选择通过校验: {}", selectedProducts.stream()
                                .map(MenuItemDTO::getName).collect(Collectors.joining(" + ")));

                        // 生成多个方案（基于候选集变体）
                        Set<String> optionSignatures = new HashSet<>();
                        for (int variant = 0; variant < 3 && options.size() < 3; variant++) {
                            List<MenuItemDTO> variantProducts = generateVariant(
                                    toolSelected, candidates, variant, intent);
                            List<Map<String, Object>> optionItems = planItems(storeId, variantProducts, text,
                                    favCodes, hotNames, ratings, intent);
                            double optionTotal = optionItems.stream()
                                    .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                                            ((Number) i.get("quantity")).intValue()).sum();
                            if (intent.budget() != null && optionTotal > intent.budget() + 0.0001) continue;

                            String signature = optionItems.stream()
                                    .map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
                            if (!optionSignatures.add(signature)) continue;

                            Map<String, Object> optionPromotion = new LinkedHashMap<>(
                                    promotionHint(storeId, optionItems, products, intent));
                            List<AgentOrderLine> optionLines = optionItems.stream()
                                    .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                                            String.valueOf(i.get("size")),
                                            ((Number) i.get("quantity")).intValue())).toList();
                            List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
                            optionPromotion.put("canAddOn", !addOnLines.isEmpty());

                            String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId,
                                    optionLines, addOnLines,
                                    "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
                            String title = "方案 " + (options.size() + 1) + " · " +
                                    variantProducts.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
                            options.add(Map.of("title", title, "items", optionItems,
                                    "planToken", optionToken, "promotion", optionPromotion));
                        }
                    } else {
                        log.info("LLM 选择结果校验失败: {}", validation.reasons());
                    }
                }
            }
        }

        // 第三层：强制精确匹配（用户明确点名商品时，直接用精确匹配结果）
        if (selectedProducts == null && !intent.explicitProductNames().isEmpty()) {
            log.info("Tool 路径未命中，尝试强制精确匹配: {}", intent.explicitProductNames());
            selectedProducts = forceExactMatch(candidates, intent.explicitProductNames(), targetItemCount);
            if (selectedProducts != null) {
                engine = "FIKA Customer Agent · exact-match";
                log.info("强制精确匹配成功: {}", selectedProducts.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + ")));
            }
        }

        // 第四层：降级到 LLM Combo Generator（旧路径，保留兼容性）
        if (selectedProducts == null && (intent.requiredCategories().size() >= 2 || intent.itemCount() >= 2)) {
            log.info("Tool 路径未命中，降级到 LLM Combo Generator");
            List<CustomerOrderComboGenerator.ComboPlan> llmCombos = comboGenerator.generateCombos(candidates, intent,
                    intent.budget() != null ? intent.budget() : Double.MAX_VALUE);

            List<CustomerOrderComboGenerator.ComboPlan> validCombos = new ArrayList<>();
            for (CustomerOrderComboGenerator.ComboPlan combo : llmCombos) {
                double totalPrice = combo.products().stream()
                        .mapToDouble(p -> minimumPrice(storeId, p)).sum();
                IntentValidator.ValidationResult result = intentValidator.validateCombo(
                        combo.products(), intent, totalPrice, intent.explicitProductNames());
                if (result.valid()) {
                    validCombos.add(combo);
                } else {
                    log.info("LLM 组合被校验层拦截: {} - {}", combo.productCodes(), result.reasons());
                }
            }

            if (!validCombos.isEmpty()) {
                selectedProducts = validCombos.get(0).products();
                engine = "FIKA Customer Agent · LLM-combo";
                log.info("使用 LLM Combo 方案: {}", selectedProducts.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + ")));

                Set<String> optionSignatures = new HashSet<>();
                for (CustomerOrderComboGenerator.ComboPlan combo : validCombos) {
                    List<Map<String, Object>> optionItems = planItems(storeId, combo.products(), text,
                            favCodes, hotNames, ratings, intent);
                    double optionTotal = optionItems.stream()
                            .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                                    ((Number) i.get("quantity")).intValue()).sum();
                    if (intent.budget() != null && optionTotal > intent.budget() + 0.0001) continue;

                    String signature = optionItems.stream()
                            .map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
                    if (!optionSignatures.add(signature)) continue;

                    Map<String, Object> optionPromotion = new LinkedHashMap<>(
                            promotionHint(storeId, optionItems, products, intent));
                    List<AgentOrderLine> optionLines = optionItems.stream()
                            .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                                    String.valueOf(i.get("size")),
                                    ((Number) i.get("quantity")).intValue())).toList();
                    List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
                    optionPromotion.put("canAddOn", !addOnLines.isEmpty());

                    String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId,
                            optionLines, addOnLines,
                            "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
                    String title = "方案 " + (options.size() + 1) + " · " +
                            combo.products().stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
                    options.add(Map.of("title", title, "items", optionItems,
                            "planToken", optionToken, "promotion", optionPromotion));
                    if (options.size() == 3) break;
                }
            }
        }

        // 第三层：降级到规则引擎（LLM 组合失败或简单单品场景）
        if (selectedProducts == null || (options.isEmpty() && selectedProducts != null)) {
            log.info("降级到规则引擎路径");
            // 如果 selectedProducts 不为空但 options 为空，用 selectedProducts 生成 option
            if (selectedProducts != null && options.isEmpty()) {
                Map<String, Object> singleOption = buildOptionFromProducts(storeId, selectedProducts, text, favCodes, hotNames, ratings, intent);
                if (singleOption != null) {
                    options.add(singleOption);
                }
            }
            List<MenuItemDTO> priceOrdered = priceSelectionTool.sort(storeId, candidates, intent.pricePreference());
            Map<String, Integer> priceRank = new HashMap<>();
            for (int index = 0; index < priceOrdered.size(); index++)
                priceRank.put(priceOrdered.get(index).getCode(), index);

            List<Scored> ranked = candidates.stream()
                    .map(p -> new Scored(p, score(p, text, favCodes, favCats, hotNames, ratings, semanticScores, intent)))
                    .sorted(Comparator.comparingInt((Scored item) -> priceRank.getOrDefault(
                            item.product().getCode(), Integer.MAX_VALUE))
                            .thenComparing(Comparator.comparingInt(Scored::score).reversed())
                            .thenComparing(x -> x.product().getId()))
                    .toList();

            List<Scored> pairingRanked = products.stream()
                    .filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())))
                    .map(p -> new Scored(p, score(p, text, favCodes, favCats, hotNames, ratings, semanticScores, intent)))
                    .sorted(Comparator.comparingInt(Scored::score).reversed()
                            .thenComparing(x -> x.product().getId()))
                    .toList();

            if (intent.requiredCategories().size() >= 2) {
                selectedProducts = buildMultiCategoryBundle(ranked, intent.requiredCategories(),
                        storeId, intent.budget(), intent.itemCount());
            } else if (intent.itemCount() >= 3) {
                selectedProducts = bestBundle(ranked, intent.itemCount(), storeId, intent.budget());
            } else {
                MenuItemDTO main = primaryCategoryFirst(ranked, intent.requiredCategories()).product();
                MenuItemDTO pairing = explicitMentionedPair(pairingRanked, main, text, intent, storeId);
                selectedProducts = new ArrayList<>(List.of(main));
                if (pairing != null) selectedProducts.add(pairing);
            }

            if (options.isEmpty()) {
                options = buildFallbackOptions(selectedProducts, ranked, pairingRanked, candidates,
                        products, text, favCodes, hotNames, ratings, intent, storeId, userId, guestId, message);
            }
            engine = "FIKA Customer Agent · rule-tools";
        }

        if (selectedProducts == null || selectedProducts.isEmpty())
            throw new ServiceException(404, "当前门店暂无可推荐的搭配");

        MenuItemDTO main = selectedProducts.get(0);
        List<Map<String, Object>> items = planItems(storeId, selectedProducts, text,
                favCodes, hotNames, ratings, intent);

        List<Map<String, Object>> signals = new ArrayList<>();
        signals.add(Map.of("label", "需求理解", "value", intent.summary(), "used", true));
        signals.add(Map.of("label", "你的偏好", "value",
                favorites.isEmpty() ? "首次探索模式" : "已参考 " + favorites.size() + " 个收藏", "used", true));
        signals.add(Map.of("label", "门店销量", "value",
                hotNames.contains(main.getName()) ? "本周热销" : "菜单匹配", "used", true));
        if (ratings.containsKey(main.getId()))
            signals.add(Map.of("label", "用户反馈", "value",
                    String.format(Locale.ROOT, "%.1f / 5", ratings.get(main.getId())), "used", true));

        String evidence = favCodes.contains(main.getCode()) ? "沿用了你常点的口味"
                : hotNames.contains(main.getName()) ? "它也是门店近期热选"
                : "它与你这次描述的口味最接近";
        String chosenNames = selectedProducts.stream()
                .map(product -> "「" + product.getName() + "」")
                .collect(Collectors.joining(" + "));
        String fallback = "如果是我这会儿来点，我会选 " + chosenNames + "。" + evidence + "。你愿意的话，我可以把这套搭配放进购物袋。";
        String menu = candidates.stream().limit(8)
                .map(MenuItemDTO::getName).collect(Collectors.joining("、"));

        Map<String, Object> store = storeMeta(storeId);
        boolean template = templateStoreCode.equalsIgnoreCase(String.valueOf(store.get("code")));
        String role = template
                ? "你是 FIKA・静安店的点单顾问。静安店是品牌表达的参考，但不要套用固定文案；像熟悉咖啡的店员一样，根据顾客当下的情绪、口味和场景自然交流。"
                : "你是 FIKA「" + store.get("name") + "」的点单顾问。根据这家门店当前的菜单和顾客当下的需求，自然交流，像一位了解菜单的店员而不是机器人。";
        String categoryConstraint = "系统已经解析出不可改变的需求约束：" + intent.summary() +
                "。主推荐和搭配只能来自系统给出的商品，不能用别的品类替代，也不能忽略排除项或预算。";

        String reply = chatClient.chat(
                role + " 用 2-4 句有温度的中文说明推荐，可以给出饮用感受或搭配理由，也可以自然地追问一个口味偏好。只能围绕系统确定的商品搭配表达，不能更改商品、数量、规格、价格、配料或承诺优惠；不要声称已经下单。" + categoryConstraint,
                "顾客需求：" + message.trim() + "\n系统推荐：" + fallback + "\n可选菜单参考：" + menu
        ).orElse(fallback);

        if (intent.budget() != null) {
            reply = "已按总预算不高于 ¥" + intent.budget() + " 为你筛选。" + reply;
        }

        if (options.isEmpty())
            throw new ServiceException(404, "当前门店没有不超过 ¥" + intent.budget() + " 的可用搭配；可提高预算或改为单品推荐");

        Map<String, Object> selected = options.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selectedItems = (List<Map<String, Object>>) selected.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> promotion = (Map<String, Object>) selected.get("promotion");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", reply);
        response.put("items", selected.get("items"));
        response.put("options", options);
        response.put("signals", signals);
        response.put("promotion", promotion);
        response.put("understanding", intent.summary());
        response.put("note", "请选择一套真实菜单方案；确认后会直接创建待支付订单并进入收银台，最终价格、库存和优惠以服务端结算为准。");
        response.put("engine", engine);
        response.put("storeName", store.get("name"));
        response.put("planToken", selected.get("planToken"));
        response.put("expiresInSeconds", 300);
        response.put("intentDebug", Map.of(
                "requiredCategories", intent.requiredCategories(),
                "itemCount", intent.itemCount(),
                "budget", intent.budget() != null ? intent.budget() : "null",
                "pairing", intent.pairing(),
                "temperature", intent.temperature().toString(),
                "excludedCategories", intent.excludedCategories(),
                "excludedProducts", intent.excludedProducts()
        ));
        return response;
    }

    /**
     * 强制精确匹配：当 LLM 选择器失败时，直接用用户点名的商品从候选集中精确提取
     *
     * 策略：
     * 1. 遍历 mustInclude 关键词列表
     * 2. 每个关键词匹配候选集中的商品名（contains 匹配）
     * 3. 按关键词数量分配商品（如 ["冰沙","冰沙"] 匹配 2 个冰沙商品）
     * 4. 如果精确匹配数量不足，用 RAG 分数最高的商品补充
     */
    private List<MenuItemDTO> forceExactMatch(List<MenuItemDTO> candidates,
                                               List<String> mustInclude,
                                               int targetCount) {
        log.info("forceExactMatch 开始: mustInclude={}, targetCount={}, candidates.size={}",
                mustInclude, targetCount, candidates.size());

        if (mustInclude == null || mustInclude.isEmpty()) return null;
        if (candidates.isEmpty()) return null;

        List<MenuItemDTO> result = new ArrayList<>();
        Set<String> usedCodes = new HashSet<>();

        // 第一步：按 mustInclude 顺序精确匹配
        for (String keyword : mustInclude) {
            if (keyword == null || keyword.isBlank()) continue;

            // 在候选集中找到第一个匹配的商品（未使用过的）
            Optional<MenuItemDTO> matched = candidates.stream()
                    .filter(p -> p.getName() != null && p.getName().contains(keyword))
                    .filter(p -> !usedCodes.contains(p.getCode()))
                    .findFirst();

            if (matched.isPresent()) {
                MenuItemDTO item = matched.get();
                result.add(item);
                usedCodes.add(item.getCode());
                log.info("精确匹配: 关键词「{}」→ 商品「{}」({})", keyword, item.getName(), item.getCode());
            } else {
                // 没找到精确匹配，尝试更模糊的匹配
                Optional<MenuItemDTO> fuzzyMatched = candidates.stream()
                        .filter(p -> p.getName() != null && containsAny(p.getName(), keyword))
                        .filter(p -> !usedCodes.contains(p.getCode()))
                        .findFirst();

                if (fuzzyMatched.isPresent()) {
                    MenuItemDTO item = fuzzyMatched.get();
                    result.add(item);
                    usedCodes.add(item.getCode());
                    log.info("模糊匹配: 关键词「{}」→ 商品「{}」({})", keyword, item.getName(), item.getCode());
                } else {
                    log.warn("未找到匹配: 关键词「{}」无对应商品", keyword);
                }
            }
        }

        log.info("精确匹配阶段完成: {}/{} 件商品", result.size(), targetCount);

        // 第二步：数量不足时，用候选集中剩余商品按 RAG 分数补充
        if (result.size() < targetCount) {
            int needMore = targetCount - result.size();
            log.info("数量不足 {} 件，从候选集补充", needMore);

            List<MenuItemDTO> remainingCandidates = candidates.stream()
                    .filter(p -> !usedCodes.contains(p.getCode()))
                    .limit(needMore)
                    .collect(Collectors.toList());

            for (MenuItemDTO p : remainingCandidates) {
                result.add(p);
                usedCodes.add(p.getCode());
            }
        }

        // 第三步：最终数量检查
        if (result.size() < targetCount) {
            log.warn("forceExactMatch 最终数量不足: {}/{}", result.size(), targetCount);
            // 从全量候选集（包括已使用的，允许重复品类）补充
            for (MenuItemDTO p : candidates) {
                if (result.size() >= targetCount) break;
                // 允许重复同品类但不允许重复同一商品
                if (!usedCodes.contains(p.getCode())) {
                    result.add(p);
                    usedCodes.add(p.getCode());
                }
            }
        }

        if (result.isEmpty()) {
            log.warn("forceExactMatch 未找到任何匹配商品");
            return null;
        }

        log.info("forceExactMatch 完成: {} 件商品 → {}", result.size(),
                result.stream().map(MenuItemDTO::getName).collect(Collectors.joining(", ")));
        return result;
    }

    /** 检查 name 中是否包含关键词中的任何字 */
    private boolean containsAny(String name, String keyword) {
        if (name == null || keyword == null) return false;
        // 简单的子串匹配
        return name.contains(keyword);
    }

    @Override
    public AgentOrderPlan confirm(String planToken, String idempotencyKey,
                                   Long storeId, Long userId, String guestId,
                                   boolean includeAddOn) {
        return planRegistry.consume(planToken, idempotencyKey, storeId, userId, guestId, includeAddOn);
    }

    private List<Map<String, Object>> buildFallbackOptions(List<MenuItemDTO> selectedProducts,
                                                          List<Scored> ranked,
                                                          List<Scored> pairingRanked,
                                                          List<MenuItemDTO> candidates,
                                                          List<MenuItemDTO> products,
                                                          String text,
                                                          Set<String> favCodes,
                                                          Set<String> hotNames,
                                                          Map<Long, Double> ratings,
                                                          CustomerOrderIntentParser.Intent intent,
                                                          Long storeId,
                                                          Long userId,
                                                          String guestId,
                                                          String message) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> optionSignatures = new HashSet<>();

        List<Map<String, Object>> primaryItems = planItems(storeId, selectedProducts, text,
                favCodes, hotNames, ratings, intent);
        double primaryTotal = primaryItems.stream()
                .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                        ((Number) i.get("quantity")).intValue()).sum();
        if (intent.budget() == null || primaryTotal <= intent.budget() + 0.0001) {
            String sig = primaryItems.stream()
                    .map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
            Map<String, Object> primaryPromo = new LinkedHashMap<>(
                    promotionHint(storeId, primaryItems, products, intent));
            List<AgentOrderLine> primaryLines = primaryItems.stream()
                    .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                            String.valueOf(i.get("size")),
                            ((Number) i.get("quantity")).intValue())).toList();
            List<AgentOrderLine> primaryAddOns = addOnLines(primaryPromo);
            primaryPromo.put("canAddOn", !primaryAddOns.isEmpty());
            String primaryToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId,
                    primaryLines, primaryAddOns,
                    "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
            String primaryTitle = "方案 1 · " + selectedProducts.stream()
                    .map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
            options.add(Map.of("title", primaryTitle, "items", primaryItems,
                    "planToken", primaryToken, "promotion", primaryPromo));
            optionSignatures.add(sig);
        }

        if (intent.requiredCategories().size() >= 2 || intent.itemCount() >= 3) {
            List<List<MenuItemDTO>> optionBundles = generateOptionBundles(ranked, intent, storeId);
            for (List<MenuItemDTO> optionProducts : optionBundles) {
                List<Map<String, Object>> optionItems = planItems(storeId, optionProducts, text,
                        favCodes, hotNames, ratings, intent);
                double optionTotal = optionItems.stream()
                        .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                                ((Number) i.get("quantity")).intValue()).sum();
                if (intent.budget() != null && optionTotal > intent.budget() + 0.0001) continue;

                String signature = optionItems.stream()
                        .map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
                if (!optionSignatures.add(signature)) continue;

                Map<String, Object> optionPromotion = new LinkedHashMap<>(
                        promotionHint(storeId, optionItems, products, intent));
                List<AgentOrderLine> optionLines = optionItems.stream()
                        .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                                String.valueOf(i.get("size")),
                                ((Number) i.get("quantity")).intValue())).toList();
                List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
                optionPromotion.put("canAddOn", !addOnLines.isEmpty());
                String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId,
                        optionLines, addOnLines,
                        "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
                String title = "方案 " + (options.size() + 1) + " · " +
                        optionProducts.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
                options.add(Map.of("title", title, "items", optionItems,
                        "planToken", optionToken, "promotion", optionPromotion));
                if (options.size() == 3) break;
            }
        } else {
            for (Scored choice : ranked) {
                MenuItemDTO optionMain = choice.product();
                MenuItemDTO optionPairing = explicitMentionedPair(pairingRanked, optionMain, text, intent, storeId);
                List<MenuItemDTO> optionProducts = new ArrayList<>(List.of(optionMain));
                if (optionPairing != null) optionProducts.add(optionPairing);

                List<Map<String, Object>> optionItems = planItems(storeId, optionProducts, text,
                        favCodes, hotNames, ratings, intent);
                double optionTotal = optionItems.stream()
                        .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                                ((Number) i.get("quantity")).intValue()).sum();
                if (intent.budget() != null && optionTotal > intent.budget() + 0.0001) continue;

                String signature = optionItems.stream()
                        .map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
                if (!optionSignatures.add(signature)) continue;

                Map<String, Object> optionPromotion = new LinkedHashMap<>(
                        promotionHint(storeId, optionItems, products, intent));
                List<AgentOrderLine> optionLines = optionItems.stream()
                        .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                                String.valueOf(i.get("size")),
                                ((Number) i.get("quantity")).intValue())).toList();
                List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
                optionPromotion.put("canAddOn", !addOnLines.isEmpty());
                String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId,
                        optionLines, addOnLines,
                        "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
                options.add(Map.of("title", "方案 " + (options.size() + 1) + " · " + optionMain.getName(),
                        "items", optionItems, "planToken", optionToken, "promotion", optionPromotion));
                if (options.size() == 3) break;
            }
        }
        return options;
    }

    private int score(MenuItemDTO p, String t, Set<String> codes, Set<String> cats,
                      Set<String> hot, Map<Long, Double> ratings,
                      Map<String, Double> semanticScores, CustomerOrderIntentParser.Intent intent) {
        int s = 10 + Math.floorMod((p.getCode() + t).hashCode(), 7);
        String c = safe(p.getCategoryCode()), temp = safe(p.getTemperature());
        if (codes.contains(p.getCode())) s += 36;
        if (cats.contains(c)) s += 15;
        if (hot.contains(p.getName())) s += 13;
        if (ratings.getOrDefault(p.getId(), 0d) >= 4.2) s += 10;
        if (directlyMentioned(t, p)) s += 90;
        if (has(t, "咖啡", "提神", "熬夜", "困", "苦") && "coffee".equals(c)) s += 21;
        if (has(t, "茶", "清爽", "轻", "低糖", "不苦") && "tea".equals(c)) s += 22;
        if (has(t, "甜", "蛋糕", "下午茶") && "dessert".equals(c)) s += 19;
        if (has(t, "饿", "午餐", "咸", "轻食", "吃的", "吃点", "正餐", "小吃") && matchesCategory(p, "food"))
            s += 28;
        s += Math.max(0, (int) Math.round(semanticScores.getOrDefault(p.getCode(), 0d) * 28));
        if (intent.temperature() == CustomerOrderIntentParser.Temperature.COLD)
            s += ("COLD".equals(temp) || "BOTH".equals(temp)) ? 20 : -18;
        if (intent.temperature() == CustomerOrderIntentParser.Temperature.HOT)
            s += ("HOT".equals(temp) || "BOTH".equals(temp)) ? 20 : -18;
        return s;
    }

    private boolean complement(String a, String b) {
        return Set.of("coffee", "tea", "ice").contains(a) ? Set.of("dessert", "food").contains(b)
                : Set.of("coffee", "tea", "ice").contains(b);
    }

    private Map<Long, Double> ratings(List<MenuItemDTO> ps) {
        Map<Long, Double> out = new HashMap<>();
        for (MenuItemDTO p : ps) {
            try {
                BigDecimal r = jdbcTemplate.queryForObject(
                        "SELECT AVG(rating) FROM feedback WHERE product_id = ? AND rating IS NOT NULL",
                        BigDecimal.class, p.getId());
                if (r != null) out.put(p.getId(), r.doubleValue());
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private Map<String, Object> storeMeta(Long storeId) {
        try {
            return jdbcTemplate.queryForMap("SELECT code,name FROM store WHERE id=?", storeId);
        } catch (Exception ignored) {
            return Map.of("code", "", "name", "当前门店");
        }
    }

    private MenuItemDTO pairing(List<Scored> items, MenuItemDTO main,
                                 CustomerOrderIntentParser.Intent intent, Long storeId) {
        if (!intent.pairing()) return null;
        for (String category : intent.requiredCategories()) {
            if (!category.equals(safe(main.getCategoryCode()))) {
                for (Scored item : items) {
                    if (matchesCategory(item.product(), category) &&
                            !item.product().getCode().equals(main.getCode()) &&
                            totalWithinBudget(storeId, main, item.product(), intent.budget()))
                        return item.product();
                }
            }
        }
        for (Scored x : items) {
            if (!x.product().getCode().equals(main.getCode()) &&
                    complement(main.getCategoryCode(), x.product().getCategoryCode()) &&
                    totalWithinBudget(storeId, main, x.product(), intent.budget()))
                return x.product();
        }
        return null;
    }

    private MenuItemDTO explicitMentionedPair(List<Scored> items, MenuItemDTO main,
                                               String text, CustomerOrderIntentParser.Intent intent,
                                               Long storeId) {
        for (Scored item : items) {
            if (!item.product().getCode().equals(main.getCode()) &&
                    directlyMentioned(text, item.product()) &&
                    totalWithinBudget(storeId, main, item.product(), intent.budget()))
                return item.product();
        }
        return pairing(items, main, intent, storeId);
    }

    private boolean directlyMentioned(String text, MenuItemDTO product) {
        String query = normalizeProductText(text);
        String name = normalizeProductText(product.getName());
        String code = normalizeProductText(product.getCode());
        if ((!name.isBlank() && query.contains(name)) || (!code.isBlank() && query.contains(code)))
            return true;
        return (name.contains("汉堡") && query.contains("汉堡")) ||
                (name.contains("薯条") && query.contains("薯条")) ||
                (name.contains("健达") && (query.contains("健达") || query.contains("奇趣蛋")));
    }

    private String normalizeProductText(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
    }

    private Scored primaryCategoryFirst(List<Scored> ranked, List<String> categories) {
        if (!categories.isEmpty())
            for (Scored item : ranked)
                if (matchesCategory(item.product(), categories.get(0))) return item;
        return ranked.get(0);
    }

    private boolean affordable(Long storeId, MenuItemDTO product, int budget) {
        return minimumPrice(storeId, product) <= budget;
    }

    private static final Map<String, Set<String>> CATEGORY_KEYWORDS = new HashMap<>();

    static {
        CATEGORY_KEYWORDS.put("tea", Set.of("奶茶", "茶饮", "果茶", "珍珠奶茶", "普洱", "红茶", "绿茶", "乌龙", "茉莉", "柠檬茶", "手打柠檬"));
        CATEGORY_KEYWORDS.put("coffee", Set.of("咖啡", "拿铁", "美式", "浓缩", "摩卡", "冷萃", "卡布奇诺"));
        CATEGORY_KEYWORDS.put("food", Set.of("轻食", "三明治", "沙拉", "贝果", "汉堡", "薯条", "炸鸡", "小吃", "鸡翅"));
        CATEGORY_KEYWORDS.put("dessert", Set.of("甜点", "蛋糕", "甜品", "曲奇", "可颂"));
        CATEGORY_KEYWORDS.put("ice", Set.of("冰淇淋", "冰激凌", "冰沙", "沙冰", "思慕雪", "冰饮", "冷饮", "冰的饮料", "冰的喝的"));
    }

    private boolean matchesTemperature(MenuItemDTO product, CustomerOrderIntentParser.Temperature temperature) {
        if (temperature == null || temperature == CustomerOrderIntentParser.Temperature.ANY) return true;
        String temp = safe(product.getTemperature()).toUpperCase(Locale.ROOT);
        if (temperature == CustomerOrderIntentParser.Temperature.COLD) {
            if ("HOT".equals(temp)) return false;
            return true;
        }
        if (temperature == CustomerOrderIntentParser.Temperature.HOT) {
            if ("COLD".equals(temp)) return false;
            return true;
        }
        return true;
    }

    private boolean matchesExcludedProductOrCategory(MenuItemDTO product, Set<String> excludedTerms,
                                                     Set<String> excludedCategories) {
        String name = safe(product.getName()).toLowerCase(Locale.ROOT);
        String desc = safe(product.getDescription()).toLowerCase(Locale.ROOT);
        String full = name + " " + desc;
        for (String term : excludedTerms) {
            String t = term.toLowerCase(Locale.ROOT);
            if (full.contains(t) || t.contains(name) || name.contains(t)) return true;
        }
        for (String cat : excludedCategories) {
            Set<String> keywords = CATEGORY_KEYWORDS.get(cat);
            if (keywords != null) {
                for (String kw : keywords) {
                    if (name.contains(kw) || desc.contains(kw)) return true;
                }
            }
        }
        return false;
    }

    private Map<String, Object> promotionHint(Long storeId, List<Map<String, Object>> selectedItems,
                                               List<MenuItemDTO> products,
                                               CustomerOrderIntentParser.Intent intent) {
        double total = selectedItems.stream()
                .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                        ((Number) i.get("quantity")).intValue()).sum();
        if (total >= 48)
            return Map.of("type", "QUALIFIED", "text", "本方案原价已满 ¥48，登录会员支付时自动享受 Agent 满 ¥48 减 ¥8。",
                    "threshold", 48, "amount", total);
        double gap = MoneyUtils.round2(48 - total);
        if (gap > 12)
            return Map.of("type", "NONE", "text", "", "threshold", 48, "amount", total);
        List<MenuItemDTO> fillers = bestTopUp(storeId, products, intent, gap);
        if (fillers.isEmpty())
            return Map.of("type", "NEAR", "text", "还差 ¥" + money(gap) + " 可享满 ¥48 减 ¥8。",
                    "threshold", 48, "amount", total);
        List<Map<String, Object>> suggestedItems = fillers.stream()
                .map(p -> item(storeId, p, "MEDIUM", Set.of(), Set.of(), Map.of())).toList();
        double added = suggestedItems.stream()
                .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue()).sum();
        String names = fillers.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
        return Map.of("type", "NEAR", "text", "还差 ¥" + money(gap) + "，加入「" + names +
                "」后合计 ¥" + money(total + added) + "，即可享满 ¥48 减 ¥8。",
                "threshold", 48, "amount", total, "suggestedItems", suggestedItems);
    }

    private List<AgentOrderLine> addOnLines(Map<String, Object> promotion) {
        Object raw = promotion.get("suggestedItems");
        if (!(raw instanceof List<?> items)) return List.of();
        List<AgentOrderLine> lines = new ArrayList<>();
        for (Object entry : items)
            if (entry instanceof Map<?, ?> item)
                lines.add(new AgentOrderLine(String.valueOf(item.get("productCode")),
                        String.valueOf(item.get("size")),
                        ((Number) item.get("quantity")).intValue()));
        return List.copyOf(lines);
    }

    private List<MenuItemDTO> bestTopUp(Long storeId, List<MenuItemDTO> products,
                                         CustomerOrderIntentParser.Intent intent, double gap) {
        List<MenuItemDTO> candidates = products.stream()
                .filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())) &&
                        !matchesExcludedProductOrCategory(p, intent.excludedProducts(), intent.excludedCategories()))
                .sorted(Comparator.comparingDouble(p -> menuService.calculatePrice(storeId, p.getCode(),
                        "MEDIUM", null, List.of())))
                .limit(12).toList();
        List<MenuItemDTO> best = List.of();
        double bestAmount = Double.MAX_VALUE;
        for (int a = 0; a < candidates.size(); a++)
            for (int b = -1; b < candidates.size(); b++)
                for (int c = -1; c < candidates.size(); c++) {
                    if ((b >= 0 && b == a) || (c >= 0 && (c == a || c == b))) continue;
                    List<MenuItemDTO> choice = new ArrayList<>();
                    choice.add(candidates.get(a));
                    if (b >= 0) choice.add(candidates.get(b));
                    if (c >= 0) choice.add(candidates.get(c));
                    double amount = choice.stream()
                            .mapToDouble(p -> menuService.calculatePrice(storeId, p.getCode(),
                                    "MEDIUM", null, List.of())).sum();
                    if (amount + 0.0001 >= gap && amount < bestAmount) {
                        best = List.copyOf(choice);
                        bestAmount = amount;
                    }
                }
        return best;
    }

    private String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private boolean totalWithinBudget(Long storeId, MenuItemDTO main,
                                       MenuItemDTO pairing, Integer budget) {
        return budget == null || minimumPrice(storeId, main) +
                minimumPrice(storeId, pairing) <= budget;
    }

    private double minimumPrice(Long storeId, MenuItemDTO product) {
        return menuService.calculatePrice(storeId, product.getCode(), "SMALL", null, List.of());
    }

    /**
     * 根据已选商品构建推荐方案 option（用于 forceExactMatch 成功后快速生成展示数据）
     */
    private Map<String, Object> buildOptionFromProducts(Long storeId, List<MenuItemDTO> products,
                                                       String text, Set<String> favCodes, Set<String> hotNames,
                                                       Map<Long, Double> ratings,
                                                       CustomerOrderIntentParser.Intent intent) {
        try {
            List<Map<String, Object>> optionItems = planItems(storeId, products, text, favCodes, hotNames, ratings, intent);
            double optionTotal = optionItems.stream()
                    .mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() *
                            ((Number) i.get("quantity")).intValue()).sum();

            List<AgentOrderLine> optionLines = optionItems.stream()
                    .map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")),
                            String.valueOf(i.get("size")),
                            ((Number) i.get("quantity")).intValue())).toList();

            Map<String, Object> optionPromotion = new LinkedHashMap<>(
                    promotionHint(storeId, optionItems, List.of(), intent));
            List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
            optionPromotion.put("canAddOn", !addOnLines.isEmpty());

            String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, null, null,
                    optionLines, addOnLines,
                    "Agent 点单：" + text.substring(0, Math.min(100, text.length()))));
            String title = products.size() + " 件商品 · " +
                    products.get(0).getName() + (products.size() > 1 ? " 等" : "");

            Map<String, Object> option = new LinkedHashMap<>();
            option.put("title", title);
            option.put("items", optionItems);
            option.put("planToken", optionToken);
            option.put("promotion", optionPromotion);
            option.put("total", optionTotal);
            return option;
        } catch (Exception e) {
            log.error("构建 option 失败", e);
            return null;
        }
    }

    private List<Map<String, Object>> planItems(Long storeId, List<MenuItemDTO> products,
                                                String text, Set<String> codes, Set<String> hot,
                                                Map<Long, Double> ratings,
                                                CustomerOrderIntentParser.Intent intent) {
        String requestedSize = has(text, "大杯", "大份", "加大") ? "LARGE"
                : has(text, "小杯", "小份") ? "SMALL" : null;
        String size = requestedSize == null ? "MEDIUM" : requestedSize;
        final String initialSize = size;
        double total = products.stream()
                .mapToDouble(product -> menuService.calculatePrice(storeId, product.getCode(),
                        initialSize, null, List.of())).sum();
        if (requestedSize == null && intent.budget() != null && total > intent.budget())
            size = "SMALL";
        final String resolvedSize = size;
        return products.stream()
                .map(product -> item(storeId, product, resolvedSize, codes, hot, ratings)).toList();
    }

    private Map<String, Object> item(Long storeId, MenuItemDTO p, String size,
                                     Set<String> codes, Set<String> hot,
                                     Map<Long, Double> ratings) {
        Double r = ratings.get(p.getId());
        String reason = codes.contains(p.getCode()) ? "根据你的收藏偏好"
                : r != null && r >= 4.2 ? "用户反馈评分较高"
                : hot.contains(p.getName()) ? "近期销量表现突出"
                : "匹配本次口味需求";
        return Map.of("productCode", p.getCode(), "name", p.getName(),
                "description", safe(p.getDescription()), "imageUrl", safe(p.getImageUrl()),
                "categoryCode", p.getCategoryCode(), "temperature", p.getTemperature(),
                "size", size, "quantity", 1,
                "estimatedPrice", menuService.calculatePrice(storeId, p.getCode(), size, null, List.of()),
                "reason", reason);
    }

    private boolean matchesCategory(MenuItemDTO product, String category) {
        if (category.equals(safe(product.getCategoryCode()))) return true;
        String text = normalizeProductText(product.getName() + " " + product.getCode() + " " + product.getDescription());
        return switch (category) {
            case "food" -> has(text, "汉堡", "薯条", "三明治", "沙拉", "贝果", "炸鸡", "炸物", "小吃", "鸡翅", "可颂", "曲奇", "蛋挞", "芝士", "蛋糕");
            case "coffee" -> has(text, "咖啡", "拿铁", "美式", "摩卡", "浓缩", "冷萃", "卡布奇诺");
            case "dessert" -> has(text, "蛋糕", "甜点", "甜品", "曲奇", "可颂");
            case "tea" -> has(text, "奶茶", "果茶", "茶饮");
            case "ice" -> has(text, "冰淇淋", "冰激凌", "冰沙", "沙冰", "思慕雪", "冰饮", "冷饮", "冰的饮料", "冰的喝的");
            default -> false;
        };
    }

    private boolean isFillerOnly(MenuItemDTO product) {
        if (product.getTopup() == null || product.getTopup() != 1) return false;
        String text = normalizeProductText(product.getName() + " " + product.getCode() + " " + product.getDescription());
        return has(text, "小料", "珍珠", "椰果", "芋圆", "矿泉水", "气泡水");
    }

    private List<MenuItemDTO> buildMultiCategoryBundle(List<Scored> ranked,
                                                       List<String> requiredCategories,
                                                       Long storeId, Integer budget, int itemCount) {
        List<MenuItemDTO> bundle = new ArrayList<>();
        Set<String> usedCodes = new HashSet<>();
        for (String category : requiredCategories) {
            for (Scored item : ranked) {
                if (usedCodes.contains(item.product().getCode())) continue;
                if (matchesCategory(item.product(), category)) {
                    bundle.add(item.product());
                    usedCodes.add(item.product().getCode());
                    break;
                }
            }
        }
        int extraNeeded = Math.max(0, itemCount - bundle.size());
        if (extraNeeded > 0) {
            for (Scored item : ranked) {
                if (usedCodes.contains(item.product().getCode())) continue;
                bundle.add(item.product());
                usedCodes.add(item.product().getCode());
                if (bundle.size() >= itemCount) break;
            }
        }
        if (budget != null) {
            double total = bundle.stream().mapToDouble(p -> minimumPrice(storeId, p)).sum();
            if (total > budget + 0.0001) {
                List<MenuItemDTO> cheaperBundle = findCheaperBundle(ranked, requiredCategories, storeId, budget, itemCount, usedCodes);
                if (cheaperBundle != null && !cheaperBundle.isEmpty()) {
                    return cheaperBundle;
                }
            }
        }
        return bundle;
    }

    private List<MenuItemDTO> findCheaperBundle(List<Scored> ranked,
                                                 List<String> requiredCategories,
                                                 Long storeId, Integer budget, int itemCount,
                                                 Set<String> originalUsedCodes) {
        List<Scored> sortedByPrice = ranked.stream()
                .filter(item -> !isFillerOnly(item.product()))
                .sorted(Comparator.comparingDouble(item -> minimumPrice(storeId, item.product())))
                .toList();
        List<MenuItemDTO> bundle = new ArrayList<>();
        Set<String> usedCodes = new HashSet<>();
        for (String category : requiredCategories) {
            for (Scored item : sortedByPrice) {
                if (usedCodes.contains(item.product().getCode())) continue;
                if (matchesCategory(item.product(), category)) {
                    bundle.add(item.product());
                    usedCodes.add(item.product().getCode());
                    break;
                }
            }
        }
        int extra = Math.max(0, itemCount - bundle.size());
        for (Scored item : sortedByPrice) {
            if (extra <= 0) break;
            if (usedCodes.contains(item.product().getCode())) continue;
            bundle.add(item.product());
            usedCodes.add(item.product().getCode());
            extra--;
        }
        double total = bundle.stream().mapToDouble(p -> minimumPrice(storeId, p)).sum();
        return total <= budget + 0.0001 ? bundle : null;
    }

    private List<List<MenuItemDTO>> generateOptionBundles(List<Scored> ranked,
                                                          CustomerOrderIntentParser.Intent intent,
                                                          Long storeId) {
        List<List<MenuItemDTO>> bundles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> categories = intent.requiredCategories();
        int minSize = categories.size();
        int targetSize = Math.max(intent.itemCount(), minSize);
        List<Scored> candidates = ranked.stream()
                .filter(item -> !isFillerOnly(item.product())).limit(20).toList();
        for (int attempt = 0; attempt < candidates.size() && bundles.size() < 3; attempt++) {
            List<MenuItemDTO> bundle = new ArrayList<>();
            Set<String> usedCodes = new HashSet<>();
            List<String> shuffledCats = new ArrayList<>(categories);
            java.util.Collections.rotate(shuffledCats, attempt);
            for (String category : shuffledCats) {
                Scored bestForCat = null;
                int startIdx = (categories.indexOf(category) + attempt) % candidates.size();
                for (int i = 0; i < candidates.size(); i++) {
                    int idx = (startIdx + i) % candidates.size();
                    Scored item = candidates.get(idx);
                    if (usedCodes.contains(item.product().getCode())) continue;
                    if (matchesCategory(item.product(), category)) {
                        if (bestForCat == null || item.score() > bestForCat.score())
                            bestForCat = item;
                    }
                }
                if (bestForCat != null) {
                    bundle.add(bestForCat.product());
                    usedCodes.add(bestForCat.product().getCode());
                }
            }
            int extra = Math.max(0, targetSize - bundle.size());
            for (int i = 0; i < candidates.size() && extra > 0; i++) {
                Scored item = candidates.get((attempt + i) % candidates.size());
                if (usedCodes.contains(item.product().getCode())) continue;
                bundle.add(item.product());
                usedCodes.add(item.product().getCode());
                extra--;
            }
            if (bundle.size() < targetSize) continue;
            if (intent.budget() != null) {
                double total = bundle.stream().mapToDouble(p -> minimumPrice(storeId, p)).sum();
                if (total > intent.budget() + 0.0001) {
                    List<MenuItemDTO> cheaper = findCheaperBundle(ranked, categories, storeId, intent.budget(), targetSize, usedCodes);
                    if (cheaper != null && !cheaper.isEmpty() && cheaper.size() >= targetSize) {
                        bundle = cheaper;
                    } else {
                        continue;
                    }
                }
            }
            String sig = bundle.stream().map(MenuItemDTO::getCode).sorted().collect(Collectors.joining(","));
            if (seen.add(sig)) bundles.add(bundle);
        }
        if (bundles.isEmpty() && !ranked.isEmpty()) {
            List<MenuItemDTO> fallback = new ArrayList<>();
            for (String category : categories) {
                for (Scored item : ranked) {
                    if (matchesCategory(item.product(), category)) {
                        fallback.add(item.product());
                        break;
                    }
                }
            }
            int extra = Math.max(0, targetSize - fallback.size());
            for (Scored item : ranked) {
                if (extra <= 0) break;
                if (!fallback.contains(item.product())) {
                    fallback.add(item.product());
                    extra--;
                }
            }
            if (!fallback.isEmpty()) bundles.add(fallback);
        }
        return bundles;
    }

    private List<MenuItemDTO> bestBundle(List<Scored> ranked, int count,
                                          Long storeId, Integer budget) {
        List<Scored> candidates = ranked.stream()
                .filter(item -> !isFillerOnly(item.product())).limit(24).toList();
        if (candidates.size() < count) count = candidates.size();
        if (count <= 0) return List.of();

        List<MenuItemDTO> best = null;
        int bestScore = Integer.MIN_VALUE;
        double bestTotal = -1;

        if (count <= 6) {
            List<List<Scored>> combos = combinations(candidates, count);
            for (List<Scored> combo : combos) {
                List<MenuItemDTO> bundle = combo.stream().map(Scored::product).toList();
                double total = bundle.stream()
                        .mapToDouble(product -> minimumPrice(storeId, product)).sum();
                if (budget != null && total > budget + 0.0001) continue;
                int score = combo.stream().mapToInt(Scored::score).sum();
                if (score > bestScore || (score == bestScore && total > bestTotal)) {
                    best = bundle;
                    bestScore = score;
                    bestTotal = total;
                }
            }
        } else {
            List<Scored> sorted = candidates.stream()
                    .sorted(Comparator.comparingInt(Scored::score).reversed()).toList();
            best = new ArrayList<>();
            for (int i = 0; i < count && i < sorted.size(); i++) {
                best.add(sorted.get(i).product());
            }
        }
        return best != null ? best : List.of();
    }

    private <T> List<List<T>> combinations(List<T> list, int k) {
        List<List<T>> result = new ArrayList<>();
        if (k > list.size() || k <= 0) return result;
        combine(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private <T> void combine(List<T> list, int k, int start, List<T> current, List<List<T>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combine(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    /** 基于主方案生成变体：保留品类结构，替换为候选集中的同分品类商品 */
    private List<MenuItemDTO> generateVariant(List<MenuItemDTO> primary,
                                               List<MenuItemDTO> candidates,
                                               int variantIndex,
                                               CustomerOrderIntentParser.Intent intent) {
        if (variantIndex == 0) return new ArrayList<>(primary);

        Map<String, List<MenuItemDTO>> byCategory = candidates.stream()
                .collect(Collectors.groupingBy(p -> safe(p.getCategoryCode())));

        List<MenuItemDTO> variant = new ArrayList<>();
        Set<String> usedCodes = new HashSet<>();

        for (MenuItemDTO p : primary) {
            String cat = safe(p.getCategoryCode());
            List<MenuItemDTO> sameCat = byCategory.getOrDefault(cat, List.of());

            if (!sameCat.isEmpty()) {
                int pickIdx = (variantIndex + primary.indexOf(p)) % sameCat.size();
                MenuItemDTO pick = sameCat.get(pickIdx);
                int guard = 0;
                while (usedCodes.contains(pick.getCode()) && guard < sameCat.size()) {
                    pickIdx = (pickIdx + 1) % sameCat.size();
                    pick = sameCat.get(pickIdx);
                    guard++;
                }
                variant.add(pick);
                usedCodes.add(pick.getCode());
            } else {
                variant.add(p);
                usedCodes.add(p.getCode());
            }
        }

        if (variant.size() < intent.itemCount()) {
            for (MenuItemDTO c : candidates) {
                if (variant.size() >= intent.itemCount()) break;
                if (!usedCodes.contains(c.getCode())) {
                    variant.add(c);
                    usedCodes.add(c.getCode());
                }
            }
        }

        return variant;
    }

    private boolean has(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private record Scored(MenuItemDTO product, int score) {
    }
}
