package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Durable Redis-backed retry queue for knowledge embeddings. MySQL remains the source of truth. */
@Service
public class KnowledgeEmbeddingSyncService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingSyncService.class);
    private static final String QUEUE_KEY = "fika:ai:knowledge:embedding:queue";
    private static final String ATTEMPT_PREFIX = "fika:ai:knowledge:embedding:attempt:";
    private static final long BASE_BACKOFF_SECONDS = 5;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final JdbcTemplate jdbc;
    private final ZhipuEmbeddingClient embeddingClient;
    private final MilvusKnowledgeVectorStore vectorStore;
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int batchSize;
    private final Clock clock;
    private volatile long lastQueueWarningMs;

    @Autowired
    public KnowledgeEmbeddingSyncService(
            JdbcTemplate jdbc,
            ZhipuEmbeddingClient embeddingClient,
            MilvusKnowledgeVectorStore vectorStore,
            StringRedisTemplate redis,
            @Value("${coffee.ai.knowledge-sync.redis-enabled:true}") boolean enabled,
            @Value("${coffee.ai.knowledge-sync.batch-size:20}") int batchSize) {
        this(jdbc, embeddingClient, vectorStore, redis, enabled, batchSize, Clock.systemUTC());
    }

    KnowledgeEmbeddingSyncService(JdbcTemplate jdbc,
                                   ZhipuEmbeddingClient embeddingClient,
                                   MilvusKnowledgeVectorStore vectorStore,
                                   StringRedisTemplate redis,
                                   boolean enabled,
                                   int batchSize,
                                   Clock clock) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.redis = redis;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.clock = clock;
        this.lastQueueWarningMs = 0;
    }

    public void enqueue(List<Long> documentIds) {
        if (!enabled || documentIds == null || documentIds.isEmpty()) return;
        long now = clock.millis();
        try {
            documentIds.stream().filter(id -> id != null && id > 0).distinct()
                    .forEach(id -> redis.opsForZSet().add(QUEUE_KEY, id.toString(), now));
        } catch (RuntimeException ex) {
            warnQueueFailure("Knowledge embedding queue unavailable; document IDs remain recoverable from MySQL", ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${coffee.ai.knowledge-sync.fixed-delay-ms:5000}",
            initialDelayString = "${coffee.ai.knowledge-sync.initial-delay-ms:10000}")
    public void drain() {
        if (!enabled) return;
        long now = clock.millis();
        try {
            Set<String> ids = redis.opsForZSet().rangeByScore(QUEUE_KEY, 0, now, 0, batchSize);
            if (ids == null || ids.isEmpty()) return;
            for (String rawId : ids) {
                if (rawId == null || !rawId.matches("[0-9]{1,19}")) {
                    redis.opsForZSet().remove(QUEUE_KEY, rawId);
                    continue;
                }
                syncOne(Long.parseLong(rawId), now);
            }
        } catch (RuntimeException ex) {
            warnQueueFailure("Knowledge embedding queue drain failed; will retry on the next schedule", ex);
        }
    }

    private void warnQueueFailure(String message, RuntimeException ex) {
        long now = clock.millis();
        if (now - lastQueueWarningMs >= 60_000L) {
            lastQueueWarningMs = now;
            log.warn(message, ex);
        }
    }

    private void syncOne(long documentId, long now) {
        List<KnowledgeRow> rows = jdbc.query("""
                SELECT id, store_id, title, content
                FROM agent_knowledge_document
                WHERE id = ? AND enabled = 1
                """, (rs, rowNum) -> new KnowledgeRow(
                rs.getLong("id"),
                (Long) rs.getObject("store_id"),
                rs.getString("title"),
                rs.getString("content")), documentId);
        if (rows.isEmpty()) {
            complete(documentId);
            return;
        }
        KnowledgeRow row = rows.get(0);
        try {
            var vectors = embeddingClient.embed(List.of(row.title() + "\n" + row.content()));
            if (vectors.isEmpty() || vectors.get().isEmpty()
                    || !vectorStore.upsert(row.id(), row.storeId(), vectors.get().get(0))) {
                updateStatus(documentId, "PENDING", "vector upsert failed");
                retry(documentId, now);
                return;
            }
            updateStatus(documentId, "READY", null);
            complete(documentId);
        } catch (RuntimeException ex) {
            log.warn("Knowledge embedding sync failed for document {}, will retry", documentId, ex);
            updateStatus(documentId, "PENDING", ex.getMessage());
            retry(documentId, now);
        }
    }

    private void retry(long documentId, long now) {
        String attemptKey = ATTEMPT_PREFIX + documentId;
        Long attempt = redis.opsForValue().increment(attemptKey);
        redis.expire(attemptKey, Duration.ofDays(2));
        long exponent = Math.min(10, Math.max(0, (attempt == null ? 1 : attempt.intValue()) - 1));
        long backoff = Math.min(MAX_BACKOFF_SECONDS, BASE_BACKOFF_SECONDS << exponent);
        redis.opsForZSet().add(QUEUE_KEY, String.valueOf(documentId), now + backoff * 1000L);
    }

    private void complete(long documentId) {
        redis.opsForZSet().remove(QUEUE_KEY, String.valueOf(documentId));
        redis.delete(ATTEMPT_PREFIX + documentId);
    }

    private void updateStatus(long documentId, String status, String error) {
        jdbc.update("UPDATE agent_knowledge_document SET embedding_status=?,embedding_error=?,embedding_updated_at=CASE WHEN ?='READY' THEN NOW() ELSE embedding_updated_at END WHERE id=?",
                status, error == null ? null : error.substring(0, Math.min(500, error.length())), status, documentId);
    }

    private record KnowledgeRow(long id, Long storeId, String title, String content) { }
}
