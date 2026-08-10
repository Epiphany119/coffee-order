package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuEmbeddingClient;
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
 * <p>MySQL 保存原文与权限，Milvus 保存向量并完成语义召回；向量服务异常时降级 MySQL
 * 关键词检索。检索结果永远带来源，禁止模型把猜测包装成事实。</p>
 */
@Service
public class AgentKnowledgeService {
    private final JdbcTemplate jdbc;
    private final ZhipuEmbeddingClient embeddingClient;
    private final MilvusKnowledgeVectorStore vectorStore;

    public AgentKnowledgeService(JdbcTemplate jdbc, ZhipuEmbeddingClient embeddingClient,
                                 MilvusKnowledgeVectorStore vectorStore) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public List<KnowledgeHit> retrieve(String query, Long storeId, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) return List.of();
        bootstrap();
        List<KnowledgeHit> semanticHits = embeddingClient.embed(List.of(normalized))
                .flatMap(vector -> vectorStore.search(vector.get(0), storeId, limit))
                .map(hits -> loadSemanticHits(hits, storeId))
                .orElse(List.of());
        if (!semanticHits.isEmpty()) return semanticHits;
        return keywordRetrieve(normalized, storeId, limit);
    }

    private List<KnowledgeHit> keywordRetrieve(String normalized, Long storeId, int limit) {
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

    /** 原文先写 MySQL，再以文档 ID 增量同步到 Milvus。 */
    public void upsert(Long storeId, String title, String content, String source) {
        upsertBatch(storeId, List.of(new KnowledgeDocument(title, content, source)));
    }

    /** 单次请求最多 64 条，适合菜单等结构化知识初始化。 */
    public void upsertBatch(Long storeId, List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) return;
        bootstrap();
        List<KnowledgeDocument> valid = documents.stream()
                .filter(document -> document != null && document.title() != null && !document.title().isBlank()
                        && document.content() != null && !document.content().isBlank())
                .map(document -> new KnowledgeDocument(document.title().trim(), document.content().trim(),
                        document.source() == null || document.source().isBlank() ? "manual" : document.source().trim()))
                .limit(64).toList();
        if (valid.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (KnowledgeDocument document : valid) {
            jdbc.update("""
                    INSERT INTO agent_knowledge_document(store_id,title,content,source,enabled,updated_at)
                    VALUES (?,?,?,?,1,?)
                    ON DUPLICATE KEY UPDATE content=VALUES(content),source=VALUES(source),enabled=1,updated_at=VALUES(updated_at)
                    """, storeId, document.title(), document.content(), document.source(), now);
        }
        List<Long> ids = valid.stream().map(document -> jdbc.queryForObject("""
                SELECT id FROM agent_knowledge_document WHERE store_id <=> ? AND title = ?
                """, Long.class, storeId, document.title())).toList();
        embeddingClient.embed(valid.stream().map(document -> document.title() + "\n" + document.content()).toList())
                .ifPresent(vectors -> {
                    for (int index = 0; index < valid.size(); index++) {
                        if (ids.get(index) != null) vectorStore.upsert(ids.get(index), storeId, vectors.get(index));
                    }
                });
    }

    private List<KnowledgeHit> loadSemanticHits(List<MilvusKnowledgeVectorStore.VectorHit> vectorHits, Long storeId) {
        if (vectorHits.isEmpty()) return List.of();
        Map<Long, MilvusKnowledgeVectorStore.VectorHit> ranks = vectorHits.stream()
                .collect(java.util.stream.Collectors.toMap(MilvusKnowledgeVectorStore.VectorHit::documentId, hit -> hit));
        String placeholders = String.join(",", java.util.Collections.nCopies(ranks.size(), "?"));
        List<Object> args = new ArrayList<>(ranks.keySet());
        args.add(storeId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, store_id, title, content, source, updated_at
                FROM agent_knowledge_document
                WHERE enabled = 1 AND id IN (%s) AND (store_id IS NULL OR store_id = ?)
                """.formatted(placeholders), args.toArray());
        return rows.stream().map(row -> {
                    long id = ((Number) row.get("id")).longValue();
                    return new KnowledgeHit(id, (Long) row.get("store_id"), String.valueOf(row.get("title")),
                            String.valueOf(row.get("content")), String.valueOf(row.get("source")),
                            row.get("updated_at") == null ? null : String.valueOf(row.get("updated_at")),
                            Math.round(ranks.get(id).score() * 100));
                }).sorted(Comparator.comparingInt(KnowledgeHit::score).reversed())
                .limit(Math.max(1, Math.min(vectorHits.size(), 10))).toList();
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

    public record KnowledgeDocument(String title, String content, String source) { }
}
