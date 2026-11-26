package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.util.MoneyUtils;
import com.coffee.module.customeragent.api.CustomerOrderAgentService;
import com.coffee.module.inventory.api.InventoryService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.web.agent.CustomerSessionDraftService.DraftLineItem;
import com.coffee.web.agent.CustomerSessionDraftService.DraftStatus;
import com.coffee.web.agent.CustomerSessionDraftService.SessionDraftOrder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 确定性顾客草稿单工具服务：负责单品/多轮加购、合并、规格反推、删改、实时计价与结账预览。
 *
 * <p>核心原则：所有商品匹配、数量计算、库存检查、价格汇总和 planToken 签发均由服务端确定性完成，
 * 杜绝 LLM 幻觉算账或“嘴上记录、实际没建单”的问题。</p>
 */
@Service
public class CustomerDraftOrderToolService {
    private static final int MAX_LINE_COUNT = 10;
    private static final int MAX_ITEM_QUANTITY = 9;
    private static final double AGENT_COUPON_THRESHOLD = 48.0;
    private static final double AGENT_COUPON_DISCOUNT = 8.0;

    private static final Pattern EXPLICIT_QTY_PATTERN = Pattern.compile(
            "(\\d{1,2}|[一二两兩三四五六七八九十])\\s*(?:个|份|杯|碗|块|件)");

    private final CustomerSessionDraftService draftService;
    private final CustomerOrderAgentService orderAgent;
    private final MenuService menuService;
    private final InventoryService inventoryService;

    public CustomerDraftOrderToolService(CustomerSessionDraftService draftService,
                                         CustomerOrderAgentService orderAgent,
                                         MenuService menuService,
                                         InventoryService inventoryService) {
        this.draftService = draftService;
        this.orderAgent = orderAgent;
        this.menuService = menuService;
        this.inventoryService = inventoryService;
    }

    /**
     * 尝试按自然语言点单/加购语句解析商品、数量和规格，并写入/合并到会话草稿单。
     */
    public ToolOutcome addByUserInput(String sessionId, String ownerKey, Long storeId,
                                      Long userId, String guestId,
                                      String rawInput, List<String> history,
                                      boolean askSizeIfMultiTier) {
        List<MenuItemDTO> available = availableProducts(storeId);
        if (available.isEmpty()) {
            return ToolOutcome.none("当前门店暂时没有有库存的可售商品，请稍后再试。");
        }
        SessionDraftOrder draft = draftService.getOrCreate(sessionId, ownerKey, storeId);
        String text = rawInput == null ? "" : rawInput.trim();

        // 1. 解析目标商品（优先当前输入点名 → 其次草稿待选商品 → 其次最近对话提到的商品）
        List<MenuItemDTO> matched = matchProducts(text, available);
        if (matched.isEmpty() && draft.hasPendingProduct()) {
            available.stream()
                    .filter(p -> Objects.equals(p.getCode(), draft.pendingProductCode()))
                    .findFirst()
                    .ifPresent(matched::add);
        }
        if (matched.isEmpty()) {
            findRecentMentionedProduct(history, available).ifPresent(matched::add);
        }

        if (matched.isEmpty()) {
            return ToolOutcome.notMatched();
        }

        // 2. 命中多款商品（如“汉堡”对应店内多款汉堡）：暂存数量并下发商品选择卡
        if (matched.size() > 1) {
            int pendingQty = extractQuantity(text, 1);
            draftService.save(draft.withPending(null, null, pendingQty));
            Map<String, Object> choicePayload = buildProductChoicePayload(matched, "请选择具体商品");
            String listText = matched.stream()
                    .limit(6)
                    .map(p -> p.getName() + "（¥" + formatPrice(defaultPrice(p)) + "）")
                    .collect(Collectors.joining("、"));
            return ToolOutcome.askChoice(
                    "当前门店有多款匹配商品：" + listText + "，你想选哪一款？点选后我直接帮你加入当前订单。",
                    "选择商品", choicePayload);
        }

        MenuItemDTO product = matched.get(0);
        int quantity = extractQuantity(text, draft.pendingQuantity() != null && Objects.equals(product.getCode(), draft.pendingProductCode())
                ? draft.pendingQuantity() : 1);
        String resolvedSize = resolveSize(text, product, history);

        // 3. 当产品存在差异化多档规格且用户显式询问/需要选份量（例如追加轻食/汉堡且未指定规格）
        boolean hasMultiPriceTier = hasDistinctPriceTiers(product);
        if (askSizeIfMultiTier && resolvedSize == null && hasMultiPriceTier) {
            draftService.save(draft.withPending(product.getCode(), product.getName(), quantity));
            Map<String, Object> sizePayload = buildSizeChoicePayload(product);
            String unit = sizeUnit(product.getCategoryCode());
            String answer = String.format(Locale.ROOT,
                    "我们店有「%s」，价格为小%s ¥%s、中%s ¥%s、大%s ¥%s。你需要什么规格和份数呢？可直接点选或回复“2个%s元的”。",
                    product.getName(),
                    unit, formatPrice(priceFor(product, "SMALL")),
                    unit, formatPrice(priceFor(product, "MEDIUM")),
                    unit, formatPrice(priceFor(product, "LARGE")),
                    formatPrice(priceFor(product, "SMALL")));
            return ToolOutcome.askChoice(answer, "选择规格", sizePayload);
        }

        String finalSize = resolvedSize == null ? "MEDIUM" : resolvedSize;
        return addConcreteItem(sessionId, ownerKey, storeId, userId, guestId, product, finalSize, quantity, List.of());
    }

    /**
     * 确定性把指定商品合并进当前会话草稿单，并签发最新 planToken。
     */
    public ToolOutcome addConcreteItem(String sessionId, String ownerKey, Long storeId,
                                       Long userId, String guestId,
                                       MenuItemDTO product, String size, int quantity, List<String> condiments) {
        if (product == null) return ToolOutcome.none("未找到对应的在售商品，请重新选择。");
        String normalizedSize = normalizeSize(size);
        int qty = Math.max(1, Math.min(MAX_ITEM_QUANTITY, quantity));
        List<String> cleanCondiments = condiments == null ? List.of() : List.copyOf(condiments);

        SessionDraftOrder draft = draftService.getOrCreate(sessionId, ownerKey, storeId);
        List<DraftLineItem> nextItems = new ArrayList<>(draft.items());

        double unitPrice = menuService.calculatePrice(storeId, product.getCode(), normalizedSize, null, cleanCondiments);
        DraftLineItem incoming = new DraftLineItem(
                product.getCode(), product.getId(), product.getName(),
                safe(product.getCategoryCode()), safe(product.getTemperature()),
                normalizedSize, qty, cleanCondiments, unitPrice,
                safe(product.getImageUrl()), safe(product.getDescription()));

        boolean mergedExisting = false;
        for (int i = 0; i < nextItems.size(); i++) {
            DraftLineItem existing = nextItems.get(i);
            if (existing.mergeKey().equals(incoming.mergeKey())) {
                int combinedQty = existing.quantity() + incoming.quantity();
                if (combinedQty > MAX_ITEM_QUANTITY) {
                    throw new ServiceException(400, "同一商品单笔最多点 " + MAX_ITEM_QUANTITY + " 份");
                }
                if (!inventoryService.hasAvailable(storeId, product.getId(), combinedQty)) {
                    throw new ServiceException(409, "「" + product.getName() + "」当前库存不足 " + combinedQty + " 份");
                }
                nextItems.set(i, new DraftLineItem(
                        existing.productCode(), existing.productId(), existing.productName(),
                        existing.categoryCode(), existing.temperature(), existing.size(),
                        combinedQty, existing.condiments(), unitPrice,
                        existing.imageUrl(), existing.description()));
                mergedExisting = true;
                break;
            }
        }
        if (!mergedExisting) {
            if (nextItems.size() >= MAX_LINE_COUNT) {
                throw new ServiceException(400, "一笔订单最多包含 " + MAX_LINE_COUNT + " 种商品");
            }
            if (!inventoryService.hasAvailable(storeId, product.getId(), qty)) {
                throw new ServiceException(409, "「" + product.getName() + "」当前库存不足 " + qty + " 份");
            }
            nextItems.add(incoming);
        }

        boolean hadPrevious = draft.hasItems();
        Map<String, Object> planPayload = issueDraftPlan(sessionId, ownerKey, storeId, userId, guestId,
                nextItems, DraftStatus.DRAFTING,
                hadPrevious ? "已将商品合并到当前待确认方案，核对无误后一次支付即可。"
                        : "已为你生成待确认点单方案，核对无误后点击去支付即可。");

        String addedDesc = formatItemShort(product.getName(), product.getCategoryCode(), normalizedSize, qty);
        String summaryText = buildOrderSummaryText(nextItems, planPayload,
                hadPrevious ? "好的，已把 " + addedDesc + " 合并进当前订单。" : "好的，已为你准备好 " + addedDesc + "。");
        return ToolOutcome.orderPlan(summaryText, hadPrevious ? "确认合并方案，去支付" : "确认方案，去支付", planPayload);
    }

    /**
     * 按文本从草稿单移除商品（例如“不要汉堡了”“去掉茉莉花茶”）。
     */
    public ToolOutcome removeByText(String sessionId, String ownerKey, Long storeId,
                                    Long userId, String guestId, String rawInput) {
        Optional<SessionDraftOrder> existingOpt = draftService.find(sessionId, ownerKey, storeId);
        if (existingOpt.isEmpty() || !existingOpt.get().hasItems()) {
            return ToolOutcome.none("当前还没有待确认的商品，不需要移除。");
        }
        SessionDraftOrder draft = existingOpt.get();
        String text = rawInput == null ? "" : rawInput.trim().toLowerCase(Locale.ROOT);
        if (text.contains("清空") || text.contains("全部") || text.contains("都不要")) {
            draftService.clear(sessionId, ownerKey, storeId);
            return ToolOutcome.none("已为你清空当前待确认方案。需要重新点单时告诉我即可。");
        }
        List<DraftLineItem> remaining = new ArrayList<>();
        List<String> removedNames = new ArrayList<>();
        for (DraftLineItem item : draft.items()) {
            String nameLower = item.productName().toLowerCase(Locale.ROOT);
            if (text.contains(nameLower) || matchesCategoryWord(text, item.categoryCode())) {
                removedNames.add(item.productName());
            } else {
                remaining.add(item);
            }
        }
        if (removedNames.isEmpty()) {
            return ToolOutcome.none("没有在当前方案里找到你想移除的商品，可以直接在下方卡片里编辑或删除。");
        }
        if (remaining.isEmpty()) {
            draftService.clear(sessionId, ownerKey, storeId);
            return ToolOutcome.none("已从当前方案移除 " + String.join("、", removedNames) + "，目前方案已清空。");
        }
        Map<String, Object> planPayload = issueDraftPlan(sessionId, ownerKey, storeId, userId, guestId,
                remaining, DraftStatus.DRAFTING, "已移除指定商品并重新计价。");
        String summaryText = buildOrderSummaryText(remaining, planPayload,
                "已为你移除 " + String.join("、", removedNames) + "。");
        return ToolOutcome.orderPlan(summaryText, "确认方案，去支付", planPayload);
    }

    /**
     * 提交给用户审核 / 结账预览：重新核验库存与实时价格，刷新 planToken，返回权威账单。
     */
    public ToolOutcome previewCheckout(String sessionId, String ownerKey, Long storeId,
                                       Long userId, String guestId, String fallbackPlanToken) {
        Optional<SessionDraftOrder> existingOpt = draftService.find(sessionId, ownerKey, storeId);
        if (existingOpt.isPresent() && existingOpt.get().hasItems()) {
            SessionDraftOrder draft = existingOpt.get();
            Map<String, Object> planPayload = issueDraftPlan(sessionId, ownerKey, storeId, userId, guestId,
                    draft.items(), DraftStatus.REVIEW_READY,
                    "请核对下方完整商品明细与金额，确认无误后点击“确认方案，去支付”。");
            String summaryText = buildOrderSummaryText(draft.items(), planPayload,
                    "已为你整理好当前全部待结算商品，请核对无误后点击下方按钮进入支付：");
            return ToolOutcome.orderPlan(summaryText, "确认方案，去支付", planPayload);
        }
        // 兼容回退：如果服务端草稿为空但前端仍带了有效 planToken，先同步恢复到草稿单
        if (fallbackPlanToken != null && !fallbackPlanToken.isBlank()) {
            try {
                Map<String, Object> viewed = orderAgent.view(fallbackPlanToken, storeId, userId, guestId);
                SessionDraftOrder synced = syncFromPlanMap(sessionId, ownerKey, storeId, viewed);
                if (synced != null && synced.hasItems()) {
                    Map<String, Object> enriched = enrichPlanPayload(viewed, synced, userId);
                    String summaryText = buildOrderSummaryText(synced.items(), enriched,
                            "已为你整理好当前全部待结算商品，请核对无误后点击下方按钮进入支付：");
                    return ToolOutcome.orderPlan(summaryText, "确认方案，去支付", enriched);
                }
            } catch (RuntimeException ignored) { }
        }
        return ToolOutcome.notMatched();
    }

    /**
     * 前端审核卡片行内编辑或删除后，同步更新服务端会话草稿单。
     */
    public Map<String, Object> syncRebuild(String sessionId, String ownerKey, Long storeId,
                                           Long userId, String guestId,
                                           String basePlanToken, List<Map<String, Object>> lines,
                                           boolean replace, String note) {
        if (replace && (lines == null || lines.isEmpty())) {
            draftService.clear(sessionId, ownerKey, storeId);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("reply", "当前方案已清空");
            empty.put("items", List.of());
            empty.put("planToken", "");
            empty.put("draftVersion", 0);
            empty.put("cleared", true);
            return empty;
        }
        Map<String, Object> rebuilt = orderAgent.rebuild(storeId, userId, guestId, basePlanToken, lines, replace, note);
        if (sessionId != null && !sessionId.isBlank() && ownerKey != null && !ownerKey.isBlank()) {
            SessionDraftOrder synced = syncFromPlanMap(sessionId, ownerKey, storeId, rebuilt);
            if (synced != null) {
                return enrichPlanPayload(rebuilt, synced, userId);
            }
        }
        return rebuilt;
    }

    /**
     * 将推荐引擎（CustomerOrderAgentService.plan）产出的方案同步到服务端会话草稿单。
     */
    public Map<String, Object> syncAndEnrichPlan(String sessionId, String ownerKey, Long storeId,
                                                 Long userId, Map<String, Object> planMap) {
        SessionDraftOrder synced = syncFromPlanMap(sessionId, ownerKey, storeId, planMap);
        if (synced == null) return planMap;
        return enrichPlanPayload(planMap, synced, userId);
    }

    public SessionDraftOrder syncFromPlanMap(String sessionId, String ownerKey, Long storeId, Map<String, Object> planMap) {
        if (planMap == null || sessionId == null || sessionId.isBlank() || ownerKey == null || ownerKey.isBlank()) {
            return null;
        }
        if (!(planMap.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) {
            return null;
        }
        List<DraftLineItem> items = new ArrayList<>();
        for (Object obj : rawItems) {
            if (!(obj instanceof Map<?, ?> raw)) continue;
            String code = String.valueOf(raw.get("productCode") == null ? "" : raw.get("productCode")).trim();
            if (code.isEmpty()) continue;
            Long productId = raw.get("productId") instanceof Number n ? n.longValue() : null;
            String name = String.valueOf(raw.get("name") == null ? code : raw.get("name"));
            String categoryCode = String.valueOf(raw.get("categoryCode") == null ? "" : raw.get("categoryCode"));
            String temperature = String.valueOf(raw.get("temperature") == null ? "" : raw.get("temperature"));
            String size = normalizeSize(raw.get("size") == null ? "MEDIUM" : String.valueOf(raw.get("size")));
            int quantity = raw.get("quantity") instanceof Number n ? n.intValue() : 1;
            double unitPrice = raw.get("estimatedPrice") instanceof Number n ? n.doubleValue() : 0.0;
            String imageUrl = String.valueOf(raw.get("imageUrl") == null ? "" : raw.get("imageUrl"));
            String description = String.valueOf(raw.get("description") == null ? "" : raw.get("description"));
            List<String> condiments = new ArrayList<>();
            if (raw.get("condiments") instanceof List<?> rawConds) {
                for (Object c : rawConds) {
                    if (c != null && !String.valueOf(c).isBlank()) condiments.add(String.valueOf(c));
                }
            }
            items.add(new DraftLineItem(code, productId, name, categoryCode, temperature,
                    size, quantity, condiments, unitPrice, imageUrl, description));
        }
        if (items.isEmpty()) return null;
        String token = planMap.get("planToken") == null ? null : String.valueOf(planMap.get("planToken"));
        SessionDraftOrder current = draftService.getOrCreate(sessionId, ownerKey, storeId);
        return draftService.save(current.withItems(items, DraftStatus.DRAFTING, token));
    }

    private Map<String, Object> issueDraftPlan(String sessionId, String ownerKey, Long storeId,
                                               Long userId, String guestId,
                                               List<DraftLineItem> items, DraftStatus status, String note) {
        List<Map<String, Object>> lines = items.stream().map(item -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productCode", item.productCode());
            line.put("size", item.size());
            line.put("quantity", item.quantity());
            line.put("condiments", item.condiments());
            return line;
        }).toList();
        Map<String, Object> rebuilt = orderAgent.rebuild(storeId, userId, guestId, null, lines, true, note);
        String newToken = String.valueOf(rebuilt.getOrDefault("planToken", ""));
        // 用服务端最新计价刷新本地行单价
        List<DraftLineItem> repricedItems = extractDraftLinesFromPlan(rebuilt, items);
        SessionDraftOrder current = draftService.getOrCreate(sessionId, ownerKey, storeId);
        SessionDraftOrder saved = draftService.save(current.withItems(repricedItems, status, newToken));
        return enrichPlanPayload(rebuilt, saved, userId);
    }

    private List<DraftLineItem> extractDraftLinesFromPlan(Map<String, Object> rebuilt, List<DraftLineItem> fallback) {
        if (!(rebuilt.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) return fallback;
        List<DraftLineItem> result = new ArrayList<>();
        for (Object obj : rawItems) {
            if (!(obj instanceof Map<?, ?> raw)) continue;
            String code = String.valueOf(raw.get("productCode") == null ? "" : raw.get("productCode"));
            Long productId = raw.get("productId") instanceof Number n ? n.longValue() : null;
            String name = String.valueOf(raw.get("name") == null ? code : raw.get("name"));
            String categoryCode = String.valueOf(raw.get("categoryCode") == null ? "" : raw.get("categoryCode"));
            String temperature = String.valueOf(raw.get("temperature") == null ? "" : raw.get("temperature"));
            String size = normalizeSize(raw.get("size") == null ? "MEDIUM" : String.valueOf(raw.get("size")));
            int quantity = raw.get("quantity") instanceof Number n ? n.intValue() : 1;
            double unitPrice = raw.get("estimatedPrice") instanceof Number n ? n.doubleValue() : 0.0;
            String imageUrl = String.valueOf(raw.get("imageUrl") == null ? "" : raw.get("imageUrl"));
            String description = String.valueOf(raw.get("description") == null ? "" : raw.get("description"));
            List<String> condiments = new ArrayList<>();
            if (raw.get("condiments") instanceof List<?> rawConds) {
                for (Object c : rawConds) if (c != null) condiments.add(String.valueOf(c));
            }
            result.add(new DraftLineItem(code, productId, name, categoryCode, temperature,
                    size, quantity, condiments, unitPrice, imageUrl, description));
        }
        return result.isEmpty() ? fallback : result;
    }

    private Map<String, Object> enrichPlanPayload(Map<String, Object> basePlan, SessionDraftOrder draft, Long userId) {
        Map<String, Object> enriched = new LinkedHashMap<>(basePlan);
        double subtotal = MoneyUtils.round2(draft.items().stream().mapToDouble(DraftLineItem::subtotal).sum());
        boolean loggedIn = userId != null && userId > 0;
        double couponDiscount = loggedIn && subtotal >= AGENT_COUPON_THRESHOLD ? AGENT_COUPON_DISCOUNT : 0.0;
        double estimatedPayable = MoneyUtils.round2(Math.max(0.0, subtotal - couponDiscount));
        String promoText;
        if (couponDiscount > 0) {
            promoText = String.format(Locale.ROOT, "已享 Agent 满 ¥48 减 ¥8（登录会员自动抵扣）");
        } else if (subtotal >= AGENT_COUPON_THRESHOLD) {
            promoText = "登录后可自动享 Agent 满 ¥48 减 ¥8";
        } else {
            double gap = MoneyUtils.round2(AGENT_COUPON_THRESHOLD - subtotal);
            promoText = String.format(Locale.ROOT, "还差 ¥%.2f 可享满 ¥48 减 ¥8", gap);
        }

        Map<String, Object> pricing = new LinkedHashMap<>();
        pricing.put("itemsSubtotal", subtotal);
        pricing.put("estimatedCouponDiscount", couponDiscount);
        pricing.put("estimatedPayable", estimatedPayable);
        pricing.put("promotionText", promoText);

        enriched.put("sessionId", draft.sessionId());
        enriched.put("draftVersion", draft.version());
        enriched.put("draftStatus", draft.status().name());
        enriched.put("fulfillmentType", draft.fulfillmentType());
        enriched.put("pricing", pricing);
        return enriched;
    }

    private String buildOrderSummaryText(List<DraftLineItem> items, Map<String, Object> planPayload, String lead) {
        String detail = items.stream()
                .map(i -> formatItemShort(i.productName(), i.categoryCode(), i.size(), i.quantity())
                        + "（¥" + formatPrice(i.subtotal()) + "）")
                .collect(Collectors.joining("、"));
        double subtotal = items.stream().mapToDouble(DraftLineItem::subtotal).sum();
        double payable = subtotal;
        if (planPayload.get("pricing") instanceof Map<?, ?> pricing && pricing.get("estimatedPayable") instanceof Number n) {
            payable = n.doubleValue();
        }
        if (payable < subtotal - 0.001) {
            return String.format(Locale.ROOT, "%s\n当前订单：%s，商品小计 ¥%.2f，预计优惠后实付 ¥%.2f。请核对下方明细后点击确认去支付。",
                    lead, detail, subtotal, payable);
        }
        return String.format(Locale.ROOT, "%s\n当前订单：%s，合计 ¥%.2f。你可以继续加购、修改规格，或直接点击下方按钮去支付。",
                lead, detail, subtotal);
    }

    private List<MenuItemDTO> availableProducts(Long storeId) {
        return menuService.getAllProducts(storeId).stream()
                .filter(p -> p != null && p.getId() != null && Boolean.TRUE.equals(p.getAvailable()))
                .filter(p -> inventoryService.hasAvailable(storeId, p.getId(), 1))
                .toList();
    }

    /** 三阶商品匹配：完整名精确命中 > 名称包含关键词 > 常见别名命中。 */
    List<MenuItemDTO> matchProducts(String text, List<MenuItemDTO> available) {
        if (text == null || text.isBlank() || available == null || available.isEmpty()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);

        // 1. 直接包含完整商品名称（按名称长度降序，避免短名误匹配）
        List<MenuItemDTO> exactNameHits = available.stream()
                .filter(p -> p.getName() != null && !p.getName().isBlank()
                        && lower.contains(p.getName().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt((MenuItemDTO p) -> p.getName().length()).reversed())
                .toList();
        if (!exactNameHits.isEmpty()) {
            // 去掉被更长已命中商品名包含的短名（例如“茉莉花茶”已命中，不再重复算“花茶”）
            List<MenuItemDTO> deduped = new ArrayList<>();
            for (MenuItemDTO candidate : exactNameHits) {
                boolean subsumed = deduped.stream()
                        .anyMatch(longer -> longer.getName().toLowerCase(Locale.ROOT)
                                .contains(candidate.getName().toLowerCase(Locale.ROOT)));
                if (!subsumed) deduped.add(candidate);
            }
            return deduped;
        }

        // 2. 常见品类/简写词匹配（例如“汉堡”匹配店内名称包含“汉堡”的商品）
        List<String> keywords = List.of("汉堡", "薯条", "三明治", "可颂", "蛋糕", "提拉米苏", "马卡龙",
                "曲奇", "布朗尼", "柠檬茶", "乌龙", "红茶", "拿铁", "美式", "冷萃", "卡布奇诺", "摩卡", "冰沙");
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                List<MenuItemDTO> kwHits = available.stream()
                        .filter(p -> p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(kw))
                        .toList();
                if (!kwHits.isEmpty()) return kwHits;
            }
        }
        return List.of();
    }

    private Optional<MenuItemDTO> findRecentMentionedProduct(List<String> history, List<MenuItemDTO> available) {
        if (history == null || history.isEmpty()) return Optional.empty();
        for (int i = history.size() - 1; i >= 0; i--) {
            String entry = history.get(i);
            if (entry == null) continue;
            List<MenuItemDTO> hits = matchProducts(entry, available);
            if (hits.size() == 1) return Optional.of(hits.get(0));
        }
        return Optional.empty();
    }

    private String resolveSize(String text, MenuItemDTO product, List<String> history) {
        if (text == null) return null;
        if (text.contains("小杯") || text.contains("小份")) return "SMALL";
        if (text.contains("中杯") || text.contains("中份")) return "MEDIUM";
        if (text.contains("大杯") || text.contains("大份") || text.contains("加大")) return "LARGE";

        // 支持“2个10元的 / 18元，冰”按价格精确反推规格
        Matcher priceMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元").matcher(text);
        if (priceMatcher.find()) {
            double targetPrice = Double.parseDouble(priceMatcher.group(1));
            for (String tier : List.of("SMALL", "MEDIUM", "LARGE")) {
                if (Math.abs(priceFor(product, tier) - targetPrice) < 0.01) {
                    return tier;
                }
            }
        }
        return null;
    }

    static int extractQuantity(String text, int defaultQty) {
        if (text == null || text.isBlank()) return defaultQty;
        Matcher matcher = EXPLICIT_QTY_PATTERN.matcher(text);
        if (matcher.find()) {
            return parseChineseOrDigit(matcher.group(1), defaultQty);
        }
        return defaultQty;
    }

    private static int parseChineseOrDigit(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.matches("\\d+")) {
            try { return Math.max(1, Math.min(MAX_ITEM_QUANTITY, Integer.parseInt(raw))); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return switch (raw) {
            case "一" -> 1;
            case "二", "两", "兩" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> fallback;
        };
    }

    private boolean hasDistinctPriceTiers(MenuItemDTO product) {
        double s = priceFor(product, "SMALL");
        double m = priceFor(product, "MEDIUM");
        double l = priceFor(product, "LARGE");
        return Math.abs(s - m) > 0.01 || Math.abs(m - l) > 0.01;
    }

    private double priceFor(MenuItemDTO product, String size) {
        Double specific = switch (size) {
            case "SMALL" -> product.getPriceSmall();
            case "LARGE" -> product.getPriceLarge();
            default -> product.getPriceMedium();
        };
        if (specific != null && specific > 0) return specific;
        return product.getBasePrice() == null ? 0.0 : product.getBasePrice();
    }

    private double defaultPrice(MenuItemDTO product) {
        return priceFor(product, "MEDIUM");
    }

    private Map<String, Object> buildSizeChoicePayload(MenuItemDTO product) {
        String unit = sizeUnit(product.getCategoryCode());
        List<Map<String, Object>> choices = List.of(
                choiceOption("小" + unit, "SMALL", priceFor(product, "SMALL")),
                choiceOption("中" + unit, "MEDIUM", priceFor(product, "MEDIUM")),
                choiceOption("大" + unit, "LARGE", priceFor(product, "LARGE"))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", "选择「" + product.getName() + "」的规格");
        payload.put("choiceKind", "SIZE");
        payload.put("productName", product.getName());
        payload.put("productCode", product.getCode());
        payload.put("choices", choices);
        return payload;
    }

    private Map<String, Object> buildProductChoicePayload(List<MenuItemDTO> products, String prompt) {
        List<Map<String, Object>> choices = products.stream().limit(8)
                .map(p -> choiceOption(p.getName(), p.getCode(), defaultPrice(p)))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", prompt);
        payload.put("choiceKind", "PRODUCT");
        payload.put("choices", choices);
        return payload;
    }

    private Map<String, Object> choiceOption(String label, String value, double price) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        row.put("price", price);
        return row;
    }

    private static String sizeUnit(String categoryCode) {
        return "food".equalsIgnoreCase(categoryCode) || "dessert".equalsIgnoreCase(categoryCode) ? "份" : "杯";
    }

    private static String formatItemShort(String name, String categoryCode, String size, int qty) {
        String unit = sizeUnit(categoryCode);
        String sizeLabel = switch (normalizeSize(size)) {
            case "SMALL" -> "小" + unit;
            case "LARGE" -> "大" + unit;
            default -> "中" + unit;
        };
        return name + "（" + sizeLabel + "）×" + qty;
    }

    private static String normalizeSize(String raw) {
        if (raw == null) return "MEDIUM";
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "SMALL", "LARGE" -> upper;
            default -> "MEDIUM";
        };
    }

    private static boolean matchesCategoryWord(String text, String categoryCode) {
        if (categoryCode == null) return false;
        return switch (categoryCode.toLowerCase(Locale.ROOT)) {
            case "food" -> text.contains("轻食") || text.contains("汉堡") || text.contains("主食");
            case "dessert" -> text.contains("甜点") || text.contains("甜品") || text.contains("蛋糕");
            case "coffee" -> text.contains("咖啡");
            case "tea" -> text.contains("茶") || text.contains("饮品");
            default -> false;
        };
    }

    private static String formatPrice(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public record ToolOutcome(boolean matched, String actionType, String actionLabel,
                              String answer, Object payload) {
        static ToolOutcome notMatched() {
            return new ToolOutcome(false, "NONE", "", "", Map.of());
        }

        static ToolOutcome none(String answer) {
            return new ToolOutcome(true, "NONE", "", answer, Map.of());
        }

        static ToolOutcome askChoice(String answer, String label, Map<String, Object> payload) {
            return new ToolOutcome(true, "ASK_CHOICE", label, answer, payload);
        }

        static ToolOutcome orderPlan(String answer, String label, Map<String, Object> payload) {
            return new ToolOutcome(true, "ORDER_PLAN", label, answer, payload);
        }
    }
}
