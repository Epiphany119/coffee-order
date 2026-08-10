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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 顾客 Agent 应用层：偏好、销量、反馈评分与需求关键词融合成可下单方案。 */
@Service
public class CustomerOrderAgentApplicationService implements CustomerOrderAgentService {
    private final MenuService menuService; private final FavoriteService favoriteService;
    private final OrderService orderService; private final JdbcTemplate jdbcTemplate; private final ZhipuChatClient chatClient; private final CustomerAgentPlanRegistry planRegistry; private final CustomerSemanticMenuRetriever semanticRetriever; private final String templateStoreCode; private final CustomerOrderIntentParser intentParser = new CustomerOrderIntentParser();
    public CustomerOrderAgentApplicationService(MenuService menuService, FavoriteService favoriteService, OrderService orderService, JdbcTemplate jdbcTemplate, ZhipuChatClient chatClient, CustomerAgentPlanRegistry planRegistry, CustomerSemanticMenuRetriever semanticRetriever, @Value("${coffee.ai.template-store-code:jingan}") String templateStoreCode) {
        this.menuService = menuService; this.favoriteService = favoriteService; this.orderService = orderService; this.jdbcTemplate = jdbcTemplate; this.chatClient = chatClient; this.planRegistry = planRegistry; this.semanticRetriever = semanticRetriever; this.templateStoreCode = templateStoreCode;
    }
    @Override public Map<String, Object> plan(Long storeId, Long userId, String guestId, String message) {
        if (message == null || message.trim().isEmpty()) throw new ServiceException(400, "告诉我你的口味、预算或饮用场景吧");
        if (message.trim().length() > 300) throw new ServiceException(400, "点单需求不能超过 300 字");
        List<MenuItemDTO> products = menuService.getAllProducts(storeId);
        if (products.isEmpty()) throw new ServiceException(404, "当前店铺暂无可点商品");
        String text = message.trim().toLowerCase(Locale.ROOT);
        CustomerOrderIntentParser.Intent intent = intentParser.parse(text);
        List<MenuItemDTO> favorites = favoriteService.getFavorites(userId, guestId);
        Set<String> favCodes = favorites.stream().map(MenuItemDTO::getCode).collect(Collectors.toSet());
        Set<String> favCats = favorites.stream().map(MenuItemDTO::getCategoryCode).collect(Collectors.toSet());
        Set<String> hotNames = orderService.getHotProducts(storeId).stream().map(r -> String.valueOf(r.getOrDefault("name", ""))).collect(Collectors.toSet());
        Map<Long, Double> ratings = ratings(products);
        // 先做语义召回，再在下方应用品类、预算、排除项等硬约束，避免“能说但不能点”。
        Map<String, Double> semanticScores = semanticRetriever.retrieve(storeId, message.trim(), products);
        List<MenuItemDTO> candidates = products.stream()
                .filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())))
                .filter(p -> !matchesExcludedProduct(p, intent.excludedProducts()))
                .filter(p -> intent.requiredCategories().isEmpty() || intent.requiredCategories().contains(safe(p.getCategoryCode())))
                .filter(p -> intent.budget() == null || affordable(storeId, p, intent.budget()))
                .toList();
        if (candidates.isEmpty()) throw new ServiceException(404, "当前门店没有同时满足“" + intent.summary() + "”的商品；可以放宽预算或让我推荐");
        List<Scored> ranked = candidates.stream().map(p -> new Scored(p, score(p, text, favCodes, favCats, hotNames, ratings, semanticScores, intent))).sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(x -> x.product().getId())).toList();
        MenuItemDTO main = primaryCategoryFirst(ranked, intent.requiredCategories()).product();
        // 搭配从全菜单中挑，但不得把用户已明确指定的主品类换成别的品类。
        List<Scored> pairingRanked = products.stream().filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())))
                .map(p -> new Scored(p, score(p, text, favCodes, favCats, hotNames, ratings, semanticScores, intent))).sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(x -> x.product().getId())).toList();
        MenuItemDTO pairing = pairing(pairingRanked, main, intent, storeId);
        List<Map<String,Object>> items = new ArrayList<>(); items.add(item(storeId, main, text, favCodes, hotNames, ratings));
        if (pairing != null) items.add(item(storeId, pairing, text, favCodes, hotNames, ratings));
        List<Map<String,Object>> signals = new ArrayList<>();
        signals.add(Map.of("label", "需求理解", "value", intent.summary(), "used", true));
        signals.add(Map.of("label", "你的偏好", "value", favorites.isEmpty() ? "首次探索模式" : "已参考 " + favorites.size() + " 个收藏", "used", true));
        signals.add(Map.of("label", "门店销量", "value", hotNames.contains(main.getName()) ? "本周热销" : "菜单匹配", "used", true));
        if (ratings.containsKey(main.getId())) signals.add(Map.of("label", "用户反馈", "value", String.format(Locale.ROOT, "%.1f / 5", ratings.get(main.getId())), "used", true));
        String evidence = favCodes.contains(main.getCode()) ? "沿用了你常点的口味" : hotNames.contains(main.getName()) ? "它也是门店近期热选" : "它与你这次描述的口味最接近";
        String fallback = "如果是我这会儿来点，我会先选「" + main.getName() + "」" + (pairing == null ? "。" : "，再配一份「" + pairing.getName() + "」。") + evidence + "。你愿意的话，我可以把这套搭配放进购物袋。";
        String menu = ranked.stream().limit(8).map(x -> x.product().getName()).collect(Collectors.joining("、"));
        Map<String,Object> store = storeMeta(storeId); boolean template = templateStoreCode.equalsIgnoreCase(String.valueOf(store.get("code")));
        String role = template ? "你是 FIKA・静安店的点单顾问。静安店是品牌表达的参考，但不要套用固定文案；像熟悉咖啡的店员一样，根据顾客当下的情绪、口味和场景自然交流。" : "你是 FIKA「" + store.get("name") + "」的点单顾问。根据这家门店当前的菜单和顾客当下的需求，自然交流，像一位了解菜单的店员而不是机器人。";
        String categoryConstraint = "系统已经解析出不可改变的需求约束：" + intent.summary() + "。主推荐和搭配只能来自系统给出的商品，不能用别的品类替代，也不能忽略排除项或预算。";
        String reply = chatClient.chat(role + " 用 2-4 句有温度的中文说明推荐，可以给出饮用感受或搭配理由，也可以自然地追问一个口味偏好。只能围绕系统确定的商品搭配表达，不能更改商品、数量、规格、价格或承诺优惠；不要声称已经下单。" + categoryConstraint,
                "顾客需求：" + message.trim() + "\n系统推荐：" + fallback + "\n可选菜单参考：" + menu).orElse(fallback);
        String engine = reply.equals(fallback) ? "FIKA Customer Agent · rule-tools" : template ? "FIKA Customer Agent · 静安样板 GLM" : "FIKA Customer Agent · 通用 GLM";
        List<Map<String,Object>> options = new ArrayList<>();
        Set<String> optionSignatures = new HashSet<>();
        // 候选不再机械取前三名：先跳过预算外方案，再去掉同一组合仅顺序不同的重复方案。
        for (Scored choice : ranked) {
            MenuItemDTO optionMain = choice.product();
            MenuItemDTO optionPairing = pairing(pairingRanked, optionMain, intent, storeId);
            List<Map<String,Object>> optionItems = new ArrayList<>(); optionItems.add(item(storeId, optionMain, text, favCodes, hotNames, ratings));
            if (optionPairing != null) optionItems.add(item(storeId, optionPairing, text, favCodes, hotNames, ratings));
            double optionTotal = optionItems.stream().mapToDouble(i -> ((Number) i.get("estimatedPrice")).doubleValue() * ((Number) i.get("quantity")).intValue()).sum();
            if (intent.budget() != null && optionTotal > intent.budget() + 0.0001) continue;
            String signature = optionItems.stream().map(i -> String.valueOf(i.get("productCode"))).sorted().collect(Collectors.joining("|"));
            if (!optionSignatures.add(signature)) continue;
            Map<String,Object> optionPromotion = new LinkedHashMap<>(promotionHint(storeId, optionItems, products, intent));
            List<AgentOrderLine> optionLines = optionItems.stream().map(i -> new AgentOrderLine(String.valueOf(i.get("productCode")), String.valueOf(i.get("size")), ((Number)i.get("quantity")).intValue())).toList();
            List<AgentOrderLine> addOnLines = addOnLines(optionPromotion);
            optionPromotion.put("canAddOn", !addOnLines.isEmpty());
            // 原搭配与凑单项写入同一个不可变订单计划，前端只持有 optionToken 一个令牌。
            String optionToken = planRegistry.issue(new AgentOrderPlan(storeId, userId, guestId, optionLines, addOnLines,
                    "Agent 点单：" + message.trim().substring(0, Math.min(100, message.trim().length()))));
            options.add(Map.of("title", "方案 " + (options.size() + 1) + " · " + optionMain.getName(), "items", optionItems, "planToken", optionToken, "promotion", optionPromotion));
            if (options.size() == 3) break;
        }
        if (options.isEmpty()) throw new ServiceException(404, "当前门店没有不超过 ¥" + intent.budget() + " 的可用搭配；可提高预算或改为单品推荐");
        Map<String,Object> selected = options.get(0);
        @SuppressWarnings("unchecked") List<Map<String,Object>> selectedItems = (List<Map<String,Object>>) selected.get("items");
        @SuppressWarnings("unchecked") Map<String,Object> promotion = (Map<String,Object>) selected.get("promotion");
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("reply", reply); response.put("items", selected.get("items")); response.put("options", options); response.put("signals", signals); response.put("promotion", promotion); response.put("understanding", intent.summary());
        response.put("note", "请选择一套真实菜单方案；确认后会直接创建待支付订单并进入收银台，最终价格、库存和优惠以服务端结算为准。"); response.put("engine", engine); response.put("storeName", store.get("name")); response.put("planToken", selected.get("planToken")); response.put("expiresInSeconds", 300);
        return response;
    }
    @Override public AgentOrderPlan confirm(String planToken, String idempotencyKey, Long storeId, Long userId, String guestId, boolean includeAddOn) { return planRegistry.consume(planToken, idempotencyKey, storeId, userId, guestId, includeAddOn); }
    private int score(MenuItemDTO p,String t,Set<String> codes,Set<String> cats,Set<String> hot,Map<Long,Double> ratings,Map<String,Double> semanticScores, CustomerOrderIntentParser.Intent intent){
        int s=10+Math.floorMod((p.getCode()+t).hashCode(),7); String c=safe(p.getCategoryCode()), temp=safe(p.getTemperature());
        if(codes.contains(p.getCode()))s+=36;if(cats.contains(c))s+=15;if(hot.contains(p.getName()))s+=13;if(ratings.getOrDefault(p.getId(),0d)>=4.2)s+=10;
        if(has(t,"咖啡","提神","熬夜","困","苦")&&"coffee".equals(c))s+=21;if(has(t,"茶","清爽","轻","低糖","不苦")&&"tea".equals(c))s+=22;if(has(t,"甜","蛋糕","下午茶")&&"dessert".equals(c))s+=19;if(has(t,"饿","午餐","咸","轻食")&&"food".equals(c))s+=21;
        // 语义相似度只参与排序，绝不覆盖上层的品类、预算、排除词安全约束。
        s += Math.max(0, (int)Math.round(semanticScores.getOrDefault(p.getCode(), 0d) * 28));
        if(intent.temperature()==CustomerOrderIntentParser.Temperature.COLD)s+=("COLD".equals(temp)||"BOTH".equals(temp))?20:-18;if(intent.temperature()==CustomerOrderIntentParser.Temperature.HOT)s+=("HOT".equals(temp)||"BOTH".equals(temp))?20:-18;return s;
    }
    private boolean complement(String a,String b){return Set.of("coffee","tea","ice").contains(a)?Set.of("dessert","food").contains(b):Set.of("coffee","tea","ice").contains(b);}
    private Map<String,Object> item(Long storeId,MenuItemDTO p,String t,Set<String> codes,Set<String> hot,Map<Long,Double> ratings){String size=has(t,"大杯","大份","加大")?"LARGE":has(t,"小杯","小份")?"SMALL":"MEDIUM";Double r=ratings.get(p.getId());String reason=codes.contains(p.getCode())?"根据你的收藏偏好":r!=null&&r>=4.2?"用户反馈评分较高":hot.contains(p.getName())?"近期销量表现突出":"匹配本次口味需求";return Map.of("productCode",p.getCode(),"name",p.getName(),"description",safe(p.getDescription()),"imageUrl",safe(p.getImageUrl()),"categoryCode",p.getCategoryCode(),"temperature",p.getTemperature(),"size",size,"quantity",1,"estimatedPrice",menuService.calculatePrice(storeId,p.getCode(),size,null,List.of()),"reason",reason);}
    private Map<Long,Double> ratings(List<MenuItemDTO> ps){Map<Long,Double> out=new HashMap<>();for(MenuItemDTO p:ps)try{BigDecimal r=jdbcTemplate.queryForObject("SELECT AVG(rating) FROM feedback WHERE product_id = ? AND rating IS NOT NULL",BigDecimal.class,p.getId());if(r!=null)out.put(p.getId(),r.doubleValue());}catch(Exception ignored){}return out;}
    private Map<String,Object> storeMeta(Long storeId){try{return jdbcTemplate.queryForMap("SELECT code,name FROM store WHERE id=?",storeId);}catch(Exception ignored){return Map.of("code","","name","当前门店");}}
    private MenuItemDTO pairing(List<Scored> items, MenuItemDTO main, CustomerOrderIntentParser.Intent intent, Long storeId) {
        if (!intent.pairing()) return null;
        // “咖啡配甜点”这类明确双品类需求优先满足第二品类，再按分数排序。
        for (String category : intent.requiredCategories()) {
            if (!category.equals(safe(main.getCategoryCode()))) {
                for (Scored item : items) if (category.equals(safe(item.product().getCategoryCode())) && !item.product().getCode().equals(main.getCode()) && totalWithinBudget(storeId, main, item.product(), intent.budget())) return item.product();
            }
        }
        for(Scored x:items)if(!x.product().getCode().equals(main.getCode())&&complement(main.getCategoryCode(),x.product().getCategoryCode())&&totalWithinBudget(storeId, main, x.product(), intent.budget()))return x.product();
        return null;
    }
    private Scored primaryCategoryFirst(List<Scored> ranked, List<String> categories) {
        if (!categories.isEmpty()) for (Scored item : ranked) if (categories.get(0).equals(safe(item.product().getCategoryCode()))) return item;
        return ranked.get(0);
    }
    private boolean affordable(Long storeId, MenuItemDTO product, int budget) { return menuService.calculatePrice(storeId, product.getCode(), "MEDIUM", null, List.of()) <= budget; }
    private boolean matchesExcludedProduct(MenuItemDTO product, Set<String> excludedTerms) { String name = safe(product.getName()).toLowerCase(Locale.ROOT); return excludedTerms.stream().map(term -> term.toLowerCase(Locale.ROOT)).anyMatch(term -> name.contains(term) || term.contains(name)); }
    private Map<String,Object> promotionHint(Long storeId, List<Map<String,Object>> selectedItems, List<MenuItemDTO> products, CustomerOrderIntentParser.Intent intent) {
        double total = selectedItems.stream().mapToDouble(i -> ((Number)i.get("estimatedPrice")).doubleValue() * ((Number)i.get("quantity")).intValue()).sum();
        if (total >= 48) return Map.of("type", "QUALIFIED", "text", "本方案原价已满 ¥48，登录会员支付时自动享受 Agent 满 ¥48 减 ¥8。", "threshold", 48, "amount", total);
        double gap = MoneyUtils.round2(48 - total);
        if (gap > 12) return Map.of("type", "NONE", "text", "", "threshold", 48, "amount", total);
        List<MenuItemDTO> fillers = bestTopUp(storeId, products, intent, gap);
        if (fillers.isEmpty()) return Map.of("type", "NEAR", "text", "还差 ¥" + money(gap) + " 可享满 ¥48 减 ¥8。", "threshold", 48, "amount", total);
        List<Map<String,Object>> suggestedItems = fillers.stream().map(p -> item(storeId, p, "", Set.of(), Set.of(), Map.of())).toList();
        double added = suggestedItems.stream().mapToDouble(i -> ((Number)i.get("estimatedPrice")).doubleValue()).sum();
        String names = fillers.stream().map(MenuItemDTO::getName).collect(Collectors.joining(" + "));
        return Map.of("type", "NEAR", "text", "还差 ¥" + money(gap) + "，加入「" + names + "」后合计 ¥" + money(total + added) + "，即可享满 ¥48 减 ¥8。", "threshold", 48, "amount", total, "suggestedItems", suggestedItems);
    }
    private List<AgentOrderLine> addOnLines(Map<String,Object> promotion) {
        Object raw = promotion.get("suggestedItems");
        if (!(raw instanceof List<?> items)) return List.of();
        List<AgentOrderLine> lines = new ArrayList<>();
        for (Object entry : items) if (entry instanceof Map<?,?> item) lines.add(new AgentOrderLine(String.valueOf(item.get("productCode")), String.valueOf(item.get("size")), ((Number)item.get("quantity")).intValue()));
        return List.copyOf(lines);
    }
    /** 穷举最多三件小额商品，选择“达到门槛后的总加价最低”的组合；绝不推荐加完仍未达标的商品。 */
    private List<MenuItemDTO> bestTopUp(Long storeId, List<MenuItemDTO> products, CustomerOrderIntentParser.Intent intent, double gap) {
        List<MenuItemDTO> candidates = products.stream().filter(p -> !intent.excludedCategories().contains(safe(p.getCategoryCode())) && !matchesExcludedProduct(p, intent.excludedProducts()))
                .sorted(Comparator.comparingDouble(p -> menuService.calculatePrice(storeId, p.getCode(), "MEDIUM", null, List.of()))).limit(12).toList();
        List<MenuItemDTO> best = List.of(); double bestAmount = Double.MAX_VALUE;
        for (int a=0;a<candidates.size();a++) for (int b=-1;b<candidates.size();b++) for (int c=-1;c<candidates.size();c++) {
            if ((b >= 0 && b == a) || (c >= 0 && (c == a || c == b))) continue;
            List<MenuItemDTO> choice = new ArrayList<>(); choice.add(candidates.get(a)); if (b >= 0) choice.add(candidates.get(b)); if (c >= 0) choice.add(candidates.get(c));
            double amount = choice.stream().mapToDouble(p -> menuService.calculatePrice(storeId, p.getCode(), "MEDIUM", null, List.of())).sum();
            if (amount + 0.0001 >= gap && amount < bestAmount) { best = List.copyOf(choice); bestAmount = amount; }
        }
        return best;
    }
    private String money(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private boolean totalWithinBudget(Long storeId, MenuItemDTO main, MenuItemDTO pairing, Integer budget) { return budget == null || menuService.calculatePrice(storeId, main.getCode(), "MEDIUM", null, List.of()) + menuService.calculatePrice(storeId, pairing.getCode(), "MEDIUM", null, List.of()) <= budget; }
    private boolean has(String t,String... words){for(String w:words)if(t.contains(w))return true;return false;} private String safe(String s){return s==null?"":s;} private record Scored(MenuItemDTO product,int score){}
}
