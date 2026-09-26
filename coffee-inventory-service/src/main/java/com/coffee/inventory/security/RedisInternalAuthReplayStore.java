package com.coffee.inventory.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "coffee.internal-auth.replay-store", havingValue = "redis")
public class RedisInternalAuthReplayStore implements InternalAuthReplayStore {
    private final StringRedisTemplate redis;

    public RedisInternalAuthReplayStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean claim(String serviceName, String nonce, Duration ttl) {
        Boolean claimed = redis.opsForValue().setIfAbsent("coffee:internal-auth:nonce:" + serviceName + ":" + nonce,
                "1", ttl);
        return Boolean.TRUE.equals(claimed);
    }
}
