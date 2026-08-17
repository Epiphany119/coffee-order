package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.web.security.RequestIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plan-Execute 编排器：先生成可审计计划，再执行白名单工具，最后仅基于工具结果生成回答。
 * 订单创建、券发放、库存修改不属于本编排器，仍需复用原有“方案确认”安全链路。
 */
@Service
public class BusinessAgentOrchestrator {
    private final AgentKnowledgeService knowledge;
    private final AgentConversationService conversations;
    private final MenuKnowledgeBootstrapService menuKnowledgeBootstrap;
    private final JdbcTemplate jdbc;
    private final ZhipuChatClient chat;

    public BusinessAgentOrchestrator(AgentKnowledgeService knowledge, AgentConversationService conversations,
                                     MenuKnowledgeBootstrapService menuKnowledgeBootstrap, JdbcTemplate jdbc, ZhipuChatClient chat) {
        this.knowledge = knowledge;
        this.conversations = conversations;
        this.menuKnowledgeBootstrap = menuKnowledgeBootstrap;
        this.jdbc = jdbc;
        this.chat = chat;
    }

    public AgentAnswer execute(RequestIdentity identity, String scene, Long storeId, String sessionId, String question) {
        String safeScene = "merchant".equalsIgnoreCase(scene) ? "merchant" : "customer";
        String owner = identity.kind().name() + ":" + (identity.id() == null ? identity.guestId() : identity.id());
        if ("merchant".equals(safeScene)) assertMerchantOwnsStore(identity, storeId);
        String session = conversations.ensureSession(sessionId, owner, safeScene);
        String input = question == null ? "" : question.trim();
        if (input.isBlank() || input.length() > 800) throw new IllegalArgumentException("问题长度需在 1 到 800 字之间");
        conversations.append(session, owner, "user", input);

        ToolPlan selectedPlan = selectToolPlan(safeScene, input);
        List<String> plan = selectedPlan.steps();
        List<ToolResult> tools = new ArrayList<>();
        // customer 只允许公共知识与当前门店菜单；商家才可读取自己当前门店聚合数据。
        if (selectedPlan.tools().contains("knowledge_retrieve")) tools.add(knowledgeTool(input, storeId));
        if (selectedPlan.tools().contains("menu_query")) tools.add(menuTool(input, storeId));
        if (selectedPlan.tools().contains("operation_metrics") && "merchant".equals(safeScene) && identity.kind() == RequestIdentity.Kind.MERCHANT) {
            tools.add(metricsTool(storeId));
        }
        String answer = answer(safeScene, input, conversations.recent(session, owner, 8), tools);
        conversations.append(session, owner, "assistant", answer);
        return new AgentAnswer(session, plan, tools, answer, selectedPlan.engine());
    }

    /** 知识写入仅开放给门店所有者；向量化由后续异步索引任务消费，不能阻塞业务请求。 */
    public void upsertKnowledge(RequestIdentity identity, Long storeId, String title, String content, String source) {
        if (identity.kind() != RequestIdentity.Kind.MERCHANT) throw new IllegalArgumentException("只有商家可维护门店知识");
        assertMerchantOwnsStore(identity, storeId);
        if (title == null || title.isBlank() || content == null || content.isBlank() || content.length() > 20_000) {
            throw new IllegalArgumentException("知识标题和正文不能为空，正文不能超过 20000 字");
        }
        knowledge.upsert(storeId, title, content, source == null || source.isBlank() ? "merchant-manual" : source);
    }

    public void bootstrapMenuKnowledge(RequestIdentity identity, Long storeId) {
        if (identity.kind() != RequestIdentity.Kind.MERCHANT) throw new IllegalArgumentException("只有商家可初始化门店知识");
        assertMerchantOwnsStore(identity, storeId);
        menuKnowledgeBootstrap.bootstrapAsync(storeId);
    }

    /**
     * 使用 GLM 做“意图 → 只读工具”选择；解析不到时退回确定性计划。
     * 模型只能从白名单中选择名称，不能传 SQL、URL、类名或写操作。
     */
    private ToolPlan selectToolPlan(String scene, String input) {
        List<String> allowed = "merchant".equals(scene)
                ? List.of("knowledge_retrieve", "menu_query", "operation_metrics")
                : List.of("knowledge_retrieve", "menu_query");
        List<String> fallback = fallbackTools(scene, input);
        // 常见业务语义可在本地零等待判断；GLM 留给模糊表达，避免每轮对话多一次第三方网络耗时。
        if (isFastPath(scene, input)) return new ToolPlan(fallback, plan(scene, input, fallback), "FIKA Agent · GLM + rule-router");
        String decision = chat.chat("你是 FIKA Agent 的工具路由器。只输出需要调用的工具英文名，使用英文逗号分隔；"
                        + "只能从以下白名单选择，禁止解释、禁止输出其他字符：" + String.join(",", allowed),
                "场景：" + scene + "\n用户问题：" + input).orElse("");
        List<String> selected = allowed.stream().filter(decision::contains).toList();
        if (selected.isEmpty()) selected = fallback;
        // 知识库是 Agent 的事实来源。即使路由模型只判断为“经营指标”，也必须先做一次
        // RAG 召回，避免营业时间、服务规则等门店事实被指标工具错误覆盖。
        if (!selected.contains("knowledge_retrieve")) {
            selected = new ArrayList<>(selected);
            selected.add(0, "knowledge_retrieve");
        }
        List<String> steps = plan(scene, input, selected);
        return new ToolPlan(selected, steps, decision.isBlank() ? "FIKA Agent · rule-tools" : "FIKA Agent · GLM tool-router");
    }

    private List<String> fallbackTools(String scene, String input) {
        List<String> tools = new ArrayList<>(List.of("knowledge_retrieve", "menu_query"));
        if ("merchant".equals(scene) && contains(input, "营业额", "订单", "库存", "履约", "增长", "告警")) tools.add("operation_metrics");
        return tools;
    }

    private boolean isFastPath(String scene, String input) {
        if ("customer".equals(scene)) return contains(input, "喝", "吃", "咖啡", "奶茶", "冰沙", "轻食", "甜品", "菜单", "优惠");
        return contains(input, "营业额", "订单", "库存", "履约", "增长", "告警", "菜单", "优惠");
    }

    private List<String> plan(String scene, String input, List<String> tools) {
        List<String> plan = new ArrayList<>();
        plan.add("识别诉求、业务范围与权限边界");
        if (tools.contains("knowledge_retrieve") || tools.contains("menu_query")) plan.add("检索门店知识与商品事实");
        if (tools.contains("operation_metrics")) plan.add("读取当前门店聚合运营指标");
        plan.add("基于可追溯证据生成建议，不执行任何写操作");
        return plan;
    }

    private ToolResult knowledgeTool(String input, Long storeId) {
        List<AgentKnowledgeService.KnowledgeHit> hits = knowledge.retrieve(input, storeId, 5);
        return new ToolResult("knowledge_retrieve", true, hits.stream().map(hit -> Map.<String, Object>of(
                "title", hit.title(), "content", clip(hit.content()), "source", hit.source(), "score", hit.score())).toList(),
                "按当前门店与公共知识范围完成检索");
    }

    private ToolResult menuTool(String input, Long storeId) {
        if (storeId == null) return new ToolResult("menu_query", false, List.of(), "未选择门店，跳过菜单查询");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT p.code,p.name,p.category_code,p.description FROM menu_item p
                    WHERE p.available = true AND (p.store_id=? OR (p.store_id=0 AND NOT EXISTS
                      (SELECT 1 FROM menu_item q WHERE q.store_id=? AND q.code=p.code)))
                    ORDER BY p.id DESC LIMIT 100
                    """, storeId, storeId);
            List<Map<String, Object>> matches = rows.stream().filter(row -> matches(input, row)).limit(8).toList();
            return new ToolResult("menu_query", true, matches, "只返回当前门店在售商品；价格和库存须由下单服务二次校验");
        } catch (Exception e) {
            return new ToolResult("menu_query", false, List.of(), "菜单数据暂不可用");
        }
    }

    private ToolResult metricsTool(Long storeId) {
        if (storeId == null) return new ToolResult("operation_metrics", false, List.of(), "未选择门店");
        try {
            Map<String, Object> metric = jdbc.queryForMap("""
                    SELECT COUNT(*) AS todayOrders, COALESCE(SUM(final_price),0) AS todayRevenue
                    FROM user_order WHERE store_id=? AND status='COMPLETED' AND DATE(created_at)=CURDATE()
                    """, storeId);
            return new ToolResult("operation_metrics", true, List.of(metric), "只统计当前门店今日已完成订单");
        } catch (Exception e) {
            return new ToolResult("operation_metrics", false, List.of(), "经营指标暂不可用");
        }
    }

    private String answer(String scene, String input, List<String> history, List<ToolResult> tools) {
        String evidence = tools.stream().map(t -> t.name() + ":" + t.data()).reduce("", (a, b) -> a + "\n" + b);
        String fallback = "我已完成「" + String.join(" → ", tools.stream().map(ToolResult::name).toList()) + "」查询。" +
                tools.stream().filter(ToolResult::success).findFirst().map(t -> t.data().isEmpty() ? "暂未检索到直接证据。" : "建议以已返回的门店数据与知识来源为准；涉及下单、发券或库存调整时仍需你确认。")
                        .orElse("当前没有可用数据，请稍后再试。");
        String system = "你是 FIKA 业务 Agent。只能依据工具结果回答；没有证据时明确说明。不要虚构菜单、价格、库存、订单或优惠；不要执行下单、扣款、发券、改库存等动作。"
                + ("merchant".equals(scene) ? "面向商家时给出可验证、低风险的运营建议。" : "面向顾客时给出自然、简洁的点单或服务说明。");
        String prompt = "问题：" + input + "\n最近上下文：" + String.join("\n", history) + "\n工具证据：" + evidence;
        return chat.chat(system, prompt).orElse(fallback);
    }

    private boolean matches(String input, Map<String, Object> row) {
        String text = (String.valueOf(row.get("name")) + " " + row.get("category_code") + " " + row.get("description")).toLowerCase(Locale.ROOT);
        String q = input.toLowerCase(Locale.ROOT);
        return q.length() < 2 || q.contains(String.valueOf(row.get("name")).toLowerCase(Locale.ROOT)) ||
                text.contains(q) || contains(q, "咖啡", "奶茶", "冰沙", "轻食", "甜品");
    }

    private boolean contains(String text, String... words) { for (String word : words) if (text.contains(word)) return true; return false; }
    private String clip(String content) { return content.length() <= 240 ? content : content.substring(0, 240) + "…"; }

    private void assertMerchantOwnsStore(RequestIdentity identity, Long storeId) {
        if (storeId == null) throw new IllegalArgumentException("经营 Agent 需要当前门店");
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM store WHERE id=? AND merchant_id=?", Integer.class, storeId, identity.id());
        if (count == null || count == 0) throw new IllegalArgumentException("无权读取该门店经营数据");
    }

    public record AgentAnswer(String sessionId, List<String> plan, List<ToolResult> tools, String answer, String engine) { }
    public record ToolResult(String name, boolean success, List<Map<String, Object>> data, String note) { }
    private record ToolPlan(List<String> tools, List<String> steps, String engine) { }
}
