package com.coffee.inventory.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "coffee.internal-auth.replay-store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryInternalAuthReplayStore implements InternalAuthReplayStore {
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();

    @Override
    public boolean claim(String serviceName, String nonce, Duration ttl) {
        long now = System.currentTimeMillis();
        nonces.entrySet().removeIf(entry -> entry.getValue() <= now);
        String key = serviceName + ":" + nonce;
        return nonces.putIfAbsent(key, now + ttl.toMillis()) == null;
    }
}
