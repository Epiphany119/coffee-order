package com.coffee.web.security;

import com.coffee.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits expensive AI operations by authenticated identity and endpoint class.
 * Redis provides a shared fixed window in a cluster; a bounded local bucket keeps
 * a single node protected when Redis is temporarily unavailable.
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AiRateLimitInterceptor.class);
    private static final String KEY_PREFIX = "fika:ai:rate:";
    private static final DefaultRedisScript<Long> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final AiRateLimitProperties properties;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();

    public AiRateLimitInterceptor(AiRateLimitProperties properties,
                                  StringRedisTemplate redis) {
        this(properties, redis, Clock.systemUTC());
    }

    AiRateLimitInterceptor(AiRateLimitProperties properties,
                           StringRedisTemplate redis,
                           Clock clock) {
        this.properties = properties;
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!properties.isEnabled()) return true;
        Limit limit = limitFor(request.getRequestURI());
        if (limit == null || limit.maxRequests() <= 0) return true;

        String identity = identityKey(request);
        String bucket = limit.name() + ":" + identity;
        Boolean redisDecision = properties.isRedisEnabled() ? tryRedis(bucket, limit) : null;
        boolean allowed = redisDecision != null ? redisDecision : tryLocal(bucket, limit);
        if (!allowed) {
            long retryAfter = Math.max(1, properties.getWindowSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            throw new ServiceException(429, "AI 请求过于频繁，请稍后再试");
        }
        return true;
    }

    private Boolean tryRedis(String bucket, Limit limit) {
        try {
            String key = KEY_PREFIX + bucket;
            Long count = redis.execute(INCREMENT_AND_EXPIRE, List.of(key),
                    String.valueOf(Math.max(1, properties.getWindowSeconds())));
            return count == null ? null : count <= limit.maxRequests();
        } catch (RuntimeException ex) {
            log.warn("AI rate limiter Redis unavailable; using bounded local fallback", ex);
            return null;
        }
    }

    private boolean tryLocal(String bucket, Limit limit) {
        long now = clock.millis();
        long window = Math.max(1, properties.getWindowSeconds()) * 1000L;
        LocalBucket current = localBuckets.compute(bucket, (key, old) -> {
            if (old == null || now - old.windowStartMs >= window) return new LocalBucket(now, new AtomicInteger(1));
            old.count.incrementAndGet();
            return old;
        });
        cleanupLocal(now, window);
        return current.count.get() <= limit.maxRequests();
    }

    private void cleanupLocal(long now, long window) {
        if (localBuckets.size() <= Math.max(100, properties.getLocalMaxEntries())) return;
        Iterator<Map.Entry<String, LocalBucket>> iterator = localBuckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LocalBucket> entry = iterator.next();
            if (now - entry.getValue().windowStartMs >= window) iterator.remove();
        }
    }

    private String identityKey(HttpServletRequest request) {
        RequestIdentity identity = RequestIdentityHolder.get();
        if (identity != null) {
            if (identity.id() != null) return identity.kind().name().toLowerCase() + ":" + identity.id();
            if (identity.guestId() != null && !identity.guestId().isBlank()) return "guest:" + identity.guestId();
        }
        String remote = request.getRemoteAddr();
        return "ip:" + (remote == null || remote.isBlank() ? "unknown" : remote);
    }

    private Limit limitFor(String path) {
        if (path == null) return null;

        if (path.equals("/api/customer-agent/assistant")) {
            return new Limit("customer-assistant", properties.getCustomerPlanRequests());
        }
        if (path.equals("/api/customer-agent/plan") || path.equals("/api/customer-agent/plan/stream")) {
            return new Limit("customer-plan", properties.getCustomerPlanRequests());
        }
        if (path.equals("/api/customer-agent/plans/confirm")) {
            return new Limit("customer-confirm", properties.getCustomerConfirmRequests());
        }
        if (path.startsWith("/api/agent-operations/evaluations/")) {
            return new Limit("evaluation", properties.getEvaluationRequests());
        }
        if (path.matches("/api/merchant/[^/]+/growth-agent/.*")) {
            return new Limit("merchant", properties.getMerchantRequests());
        }
        return null;
    }

    private record Limit(String name, int maxRequests) { }
    private static final class LocalBucket {
        private final long windowStartMs;
        private final AtomicInteger count;
        private LocalBucket(long windowStartMs, AtomicInteger count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
