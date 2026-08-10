package com.coffee.web.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 业务知识库的统一入口。
 *
 * <p>当前实现以 MySQL 文档索引作为可靠降级；启用 Milvus 后只需替换 retrieve 方法的
 * 检索器实现，调用方和权限模型保持不变。检索结果永远带来源，禁止模型把猜测包装成事实。</p>
 */
@Service
public class AgentKnowledgeService {
    private final JdbcTemplate jdbc;

    public AgentKnowledgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeHit> retrieve(String query, Long storeId, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) return List.of();
        bootstrap();
        List<String> terms = tokenize(normalized);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, store_id, title, content, source, updated_at
                FROM agent_knowledge_document
                WHERE enabled = 1 AND (store_id IS NULL OR store_id = ?)
                ORDER BY updated_at DESC LIMIT 200
                """, storeId);
        return rows.stream()
                .map(row -> new KnowledgeHit(
                        ((Number) row.get("id")).longValue(),
                        (Long) row.get("store_id"),
                        String.valueOf(row.get("title")),
                        String.valueOf(row.get("content")),
                        String.valueOf(row.get("source")),
                        row.get("updated_at") == null ? null : String.valueOf(row.get("updated_at")),
                        score(row, terms)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingInt(KnowledgeHit::score).reversed().thenComparing(KnowledgeHit::id))
                .limit(Math.max(1, Math.min(limit, 10)))
                .toList();
    }

    /** 为管理端导入、后续 ETL 和 Milvus 增量同步提供一致的写入口。 */
    public void upsert(Long storeId, String title, String content, String source) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) return;
        bootstrap();
        jdbc.update("""
                INSERT INTO agent_knowledge_document(store_id,title,content,source,enabled,updated_at)
                VALUES (?,?,?,?,1,?)
                ON DUPLICATE KEY UPDATE content=VALUES(content),source=VALUES(source),enabled=1,updated_at=VALUES(updated_at)
                """, storeId, title.trim(), content.trim(), source == null ? "manual" : source, LocalDateTime.now());
    }

    private int score(Map<String, Object> row, List<String> terms) {
        String haystack = (String.valueOf(row.get("title")) + " " + row.get("content")).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) score += term.length() > 1 ? 8 : 2;
        }
        return score;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ");
        for (String word : normalized.split("\\s+")) if (word.length() > 1) tokens.add(word);
        for (int i = 0; i + 1 < normalized.length(); i++) {
            String pair = normalized.substring(i, i + 2);
            if (pair.matches("[\\u4e00-\\u9fa5]{2}")) tokens.add(pair);
        }
        return tokens.stream().distinct().toList();
    }

    private void bootstrap() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS agent_knowledge_document (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  store_id BIGINT NULL,
                  title VARCHAR(200) NOT NULL,
                  content TEXT NOT NULL,
                  source VARCHAR(80) NOT NULL,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  updated_at DATETIME NOT NULL,
                  UNIQUE KEY uk_agent_knowledge (store_id, title)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    public record KnowledgeHit(long id, Long storeId, String title, String content,
                               String source, String updatedAt, int score) { }
}
