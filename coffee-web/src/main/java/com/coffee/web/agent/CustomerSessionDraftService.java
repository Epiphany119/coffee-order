package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端会话草稿单（Session Draft Order）状态仓库。
 *
 * <p>作为多轮点单、追加合并、编辑规格与结账审核的唯一真相源（Single Source of Truth），
 * 按 (sessionId, ownerKey, storeId) 严格隔离，TTL 为 30 分钟且每次交互自动续期。</p>
 */
@Service
public class CustomerSessionDraftService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String REDIS_PREFIX = "fika:agent:session-draft:";

    private final ConcurrentHashMap<String, LocalEntry> localStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    public CustomerSessionDraftService(ObjectMapper objectMapper) {
        this(objectMapper, null, false);
    }

    @Autowired
    public CustomerSessionDraftService(ObjectMapper objectMapper,
                                       ObjectProvider<StringRedisTemplate> redisProvider,
                                       @Value("${coffee.ai.plan-registry.redis-enabled:false}") boolean redisEnabled) {
        this.objectMapper = objectMapper;
        this.redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        this.redisEnabled = redisEnabled && this.redis != null;
    }

    public Optional<SessionDraftOrder> find(String sessionId, String ownerKey, Long storeId) {
        if (isBlank(sessionId) || isBlank(ownerKey) || storeId == null || storeId <= 0) {
            return Optional.empty();
        }
        String key = storageKey(sessionId, ownerKey, storeId);
        if (redisEnabled) {
            try {
                String json = redis.opsForValue().get(REDIS_PREFIX + key);
                if (json == null || json.isBlank()) return Optional.empty();
                SessionDraftOrder draft = objectMapper.readValue(json, SessionDraftOrder.class);
                redis.expire(REDIS_PREFIX + key, TTL);
                return Optional.of(draft);
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        purgeExpired();
        LocalEntry entry = localStore.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            localStore.remove(key);
            return Optional.empty();
        }
        localStore.put(key, new LocalEntry(entry.draft(), Instant.now().plus(TTL)));
        return Optional.of(entry.draft());
    }

    public SessionDraftOrder getOrCreate(String sessionId, String ownerKey, Long storeId) {
        return find(sessionId, ownerKey, storeId).orElseGet(() -> new SessionDraftOrder(
                sessionId, ownerKey, storeId, 0, DraftStatus.EMPTY, "PICKUP",
                null, null, null, null, List.of(), Instant.now().toString()));
    }

    public SessionDraftOrder save(SessionDraftOrder draft) {
        if (draft == null || isBlank(draft.sessionId()) || isBlank(draft.ownerKey()) || draft.storeId() == null) {
            throw new ServiceException(400, "无效的会话草稿单");
        }
        SessionDraftOrder updated = new SessionDraftOrder(
                draft.sessionId(),
                draft.ownerKey(),
                draft.storeId(),
                draft.version() + 1,
                draft.items() == null || draft.items().isEmpty() ? DraftStatus.EMPTY : draft.status(),
                draft.fulfillmentType() == null || draft.fulfillmentType().isBlank() ? "PICKUP" : draft.fulfillmentType(),
                draft.pendingProductCode(),
                draft.pendingProductName(),
                draft.pendingQuantity(),
                draft.latestPlanToken(),
                draft.items() == null ? List.of() : List.copyOf(draft.items()),
                Instant.now().toString()
        );
        String key = storageKey(updated.sessionId(), updated.ownerKey(), updated.storeId());
        if (redisEnabled) {
            try {
                String json = objectMapper.writeValueAsString(updated);
                redis.opsForValue().set(REDIS_PREFIX + key, json, TTL);
            } catch (Exception ex) {
                throw new ServiceException(503, "会话草稿单保存失败");
            }
        } else {
            purgeExpired();
            localStore.put(key, new LocalEntry(updated, Instant.now().plus(TTL)));
        }
        return updated;
    }

    public void clear(String sessionId, String ownerKey, Long storeId) {
        if (isBlank(sessionId) || isBlank(ownerKey) || storeId == null) return;
        String key = storageKey(sessionId, ownerKey, storeId);
        if (redisEnabled) {
            try {
                redis.delete(REDIS_PREFIX + key);
            } catch (Exception ignored) { }
        } else {
            localStore.remove(key);
        }
    }

    public void markSubmitted(String sessionId, String ownerKey, Long storeId) {
        if (isBlank(sessionId) || isBlank(ownerKey) || storeId == null) return;
        find(sessionId, ownerKey, storeId).ifPresent(draft -> {
            SessionDraftOrder submitted = new SessionDraftOrder(
                    draft.sessionId(), draft.ownerKey(), draft.storeId(),
                    draft.version(), DraftStatus.SUBMITTED, draft.fulfillmentType(),
                    null, null, null, null, List.of(), Instant.now().toString());
            save(submitted);
        });
    }

    public boolean hasActiveItems(String sessionId, String ownerKey, Long storeId) {
        return find(sessionId, ownerKey, storeId)
                .map(draft -> draft.items() != null && !draft.items().isEmpty())
                .orElse(false);
    }

    private String storageKey(String sessionId, String ownerKey, Long storeId) {
        return sessionId + "|" + ownerKey + "|" + storeId;
    }

    private void purgeExpired() {
        if (localStore.size() > 300) {
            Instant now = Instant.now();
            localStore.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum DraftStatus {
        EMPTY, DRAFTING, REVIEW_READY, SUBMITTED
    }

    public record DraftLineItem(
            String productCode,
            Long productId,
            String productName,
            String categoryCode,
            String temperature,
            String size,
            int quantity,
            List<String> condiments,
            double unitPrice,
            String imageUrl,
            String description
    ) {
        public DraftLineItem {
            condiments = condiments == null ? List.of() : List.copyOf(condiments);
            size = size == null || size.isBlank() ? "MEDIUM" : size.toUpperCase();
            quantity = Math.max(1, Math.min(9, quantity));
        }

        public String mergeKey() {
            List<String> sortedCondiments = new ArrayList<>(condiments);
            sortedCondiments.sort(String::compareTo);
            return productCode + "|" + size + "|" + String.join(",", sortedCondiments);
        }

        public double subtotal() {
            return Math.round(unitPrice * quantity * 100.0) / 100.0;
        }
    }

    public record SessionDraftOrder(
            String sessionId,
            String ownerKey,
            Long storeId,
            int version,
            DraftStatus status,
            String fulfillmentType,
            String pendingProductCode,
            String pendingProductName,
            Integer pendingQuantity,
            String latestPlanToken,
            List<DraftLineItem> items,
            String updatedAt
    ) {
        public boolean hasItems() {
            return items != null && !items.isEmpty();
        }

        public boolean hasPendingProduct() {
            return pendingProductCode != null && !pendingProductCode.isBlank();
        }

        public SessionDraftOrder withPending(String productCode, String productName, Integer quantity) {
            return new SessionDraftOrder(sessionId, ownerKey, storeId, version, status, fulfillmentType,
                    productCode, productName, quantity, latestPlanToken, items, updatedAt);
        }

        public SessionDraftOrder withItems(List<DraftLineItem> nextItems, DraftStatus nextStatus, String nextPlanToken) {
            return new SessionDraftOrder(sessionId, ownerKey, storeId, version, nextStatus, fulfillmentType,
                    null, null, null, nextPlanToken, nextItems, updatedAt);
        }

        public boolean matchesIdentity(String expectedSessionId, String expectedOwnerKey, Long expectedStoreId) {
            return Objects.equals(sessionId, expectedSessionId)
                    && Objects.equals(ownerKey, expectedOwnerKey)
                    && Objects.equals(storeId, expectedStoreId);
        }
    }

    private record LocalEntry(SessionDraftOrder draft, Instant expiresAt) { }
}
