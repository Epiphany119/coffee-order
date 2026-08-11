package com.coffee.web.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 从当前有效菜单生成可审计的 RAG 商品知识；不生成数据库中不存在的商品或规则。 */
@Service
public class MenuKnowledgeBootstrapService {
    private final JdbcTemplate jdbc;
    private final AgentKnowledgeService knowledge;

    public MenuKnowledgeBootstrapService(JdbcTemplate jdbc, AgentKnowledgeService knowledge) {
        this.jdbc = jdbc;
        this.knowledge = knowledge;
    }

    /**
     * 为每个真实商品生成八类可审计知识：商品事实、识别、规格、组合，以及预算规划补充规则。
     * 50 个菜单商品最终形成 400 条知识；所有价格和商品名均来自 menu_item。
     */
    public int bootstrap(Long storeId) {
        String storeName = jdbc.queryForObject("SELECT name FROM store WHERE id = ?", String.class, storeId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.code,p.name,p.category_code,p.base_price,p.price_small,p.price_medium,p.price_large,
                       p.description,p.store_id
                FROM menu_item p
                WHERE p.available = true AND (p.store_id = ? OR (p.store_id = 0 AND NOT EXISTS
                    (SELECT 1 FROM menu_item q WHERE q.store_id = ? AND q.code = p.code)))
                ORDER BY CASE WHEN p.store_id = ? THEN 0 ELSE 1 END,p.id
                LIMIT 50
                """, storeId, storeId, storeId);
        List<AgentKnowledgeService.KnowledgeDocument> documents = rows.stream()
                .flatMap(row -> documentsFor(storeName, row).stream()).toList();
        Map<String, String> existing = jdbc.queryForList("""
                SELECT title, content FROM agent_knowledge_document
                WHERE store_id = ? AND enabled = 1
                """, storeId).stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get("title")), row -> String.valueOf(row.get("content")), (left, right) -> left));
        List<AgentKnowledgeService.KnowledgeDocument> changed = documents.stream()
                .filter(document -> !document.content().equals(existing.get(document.title())))
                .toList();
        // Embedding 服务按小批量调用，避免一次传入 200 条时超出供应商批量限制。
        for (int start = 0; start < changed.size(); start += 64) {
            knowledge.upsertBatch(storeId, changed.subList(start, Math.min(start + 64, changed.size())));
        }
        return changed.size();
    }

    private List<AgentKnowledgeService.KnowledgeDocument> documentsFor(String storeName, Map<String, Object> row) {
        String name = String.valueOf(row.get("name"));
        String code = String.valueOf(row.get("code"));
        String category = String.valueOf(row.get("category_code"));
        String description = safe(row.get("description"));
        String small = price(row.get("price_small"), row.get("base_price"));
        String medium = price(row.get("price_medium"), row.get("base_price"));
        String large = price(row.get("price_large"), row.get("base_price"));
        String scope = ((Number) row.get("store_id")).longValue() == 0L ? "当前门店可售的共享菜单" : "本店专属菜单";
        return List.of(
                new AgentKnowledgeService.KnowledgeDocument("菜单商品｜" + name,
                        String.format("门店：%s。商品名称：%s。商品编码：%s。品类：%s。商品说明：%s。规格参考价：小杯%s元，中杯%s元，大杯%s元。%s。最终价格、库存与可售状态以实时下单页为准。",
                                storeName, name, code, category, description, small, medium, large, scope), "menu-catalog-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("点单识别｜" + name,
                        String.format("在%s，用户提到“%s”或商品编码“%s”时，应优先精确匹配这款真实菜单商品，不要用名称相近的饮品或甜品替代。它属于%s，说明为：%s。",
                                storeName, name, code, category, description), "menu-semantic-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("规格预算｜" + name,
                        String.format("%s的菜单规格参考价：小杯%s元、中杯%s元、大杯%s元。用户提出预算、杯型或份数时，先按该商品真实规格估算；若用户没有指定规格，展示候选并由用户确认。",
                                name, small, medium, large), "menu-semantic-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("组合执行｜" + name,
                        String.format("当用户在同一句中明确点名“%s”和其他商品时，点单 Agent 应把所有被明确点名且当前可售、预算允许的商品同时放入预览方案；先展示方案，只有用户确认后才创建订单。",
                                name), "menu-semantic-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("预算单品｜" + name,
                        String.format("用户说“预算有限、买个%s、在预算内吃点或喝点”时，先把它作为单品候选，而不是强行组成套餐。%s的基础规格参考价为%s元，中等规格%s元，加大规格%s元；预算不足基础规格时应明确说明，不能虚构降价。",
                                name, name, small, medium, large), "menu-planning-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("需求判断｜" + name,
                        String.format("“想吃的、吃点、正餐、小吃、来个%s”都是点单意图，不等于必须购买多件。若用户只表达预算和餐食诉求，%s可作为独立方案，与其他预算内单品并列展示给用户选择。",
                                name, name), "menu-planning-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("组合预算｜" + name,
                        String.format("将%s与另一商品组成方案前，必须用当前门店的真实规格价计算总额。默认规格超出用户总预算时，若用户未指定规格，可尝试两件均使用基础规格；仍超预算则展示单品方案或建议提高预算，不能悄悄超支。",
                                name), "menu-planning-bootstrap"),
                new AgentKnowledgeService.KnowledgeDocument("优惠核验｜" + name,
                        String.format("推荐%s时，优惠券、会员折扣和秒杀是否可叠加必须由当前登录用户的有效券、活动状态及下单结算规则实时核验。没有真实可用优惠时，不得承诺折扣；展示金额仅作预估，确认下单时由服务端复核。",
                                name), "menu-planning-bootstrap"));
    }

    private String safe(Object value) { return value == null ? "暂无商品描述" : String.valueOf(value); }
    private String price(Object specific, Object fallback) { Object value = specific == null ? fallback : specific; return value == null ? "以菜单为准" : String.valueOf(value); }
}
