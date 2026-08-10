package com.coffee.web.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 从当前有效菜单生成可审计的 RAG 商品知识；不生成数据库中不存在的商品或规则。 */
@Service
public class MenuKnowledgeBootstrapService {
    private final JdbcTemplate jdbc;
    private final AgentKnowledgeService knowledge;

    public MenuKnowledgeBootstrapService(JdbcTemplate jdbc, AgentKnowledgeService knowledge) {
        this.jdbc = jdbc;
        this.knowledge = knowledge;
    }

    /** 每店最多 50 条，本店商品优先，再补充当前有效的共享菜单。 */
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
        List<AgentKnowledgeService.KnowledgeDocument> documents = rows.stream().map(row -> {
            String title = "菜单商品｜" + row.get("name");
            String content = String.format("门店：%s。商品名称：%s。商品编码：%s。品类：%s。商品说明：%s。规格参考价：小杯%s元，中杯%s元，大杯%s元。%s最终价格、库存与可售状态以实时下单页为准。",
                    storeName, row.get("name"), row.get("code"), row.get("category_code"), safe(row.get("description")),
                    price(row.get("price_small"), row.get("base_price")), price(row.get("price_medium"), row.get("base_price")),
                    price(row.get("price_large"), row.get("base_price")),
                    ((Number) row.get("store_id")).longValue() == 0L ? "这是当前门店可售的共享菜单。" : "这是本店专属菜单。 ");
            return new AgentKnowledgeService.KnowledgeDocument(title, content, "menu-catalog-bootstrap");
        }).toList();
        knowledge.upsertBatch(storeId, documents);
        return documents.size();
    }

    private String safe(Object value) { return value == null ? "暂无商品描述" : String.valueOf(value); }
    private String price(Object specific, Object fallback) { Object value = specific == null ? fallback : specific; return value == null ? "以菜单为准" : String.valueOf(value); }
}
