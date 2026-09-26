package com.coffee.inventory.security;

import java.time.Duration;

public interface InternalAuthReplayStore {
    boolean claim(String serviceName, String nonce, Duration ttl);
}
