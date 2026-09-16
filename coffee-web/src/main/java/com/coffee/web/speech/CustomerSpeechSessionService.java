package com.coffee.web.speech;

import com.coffee.web.security.RequestIdentity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为浏览器端语音 WebSocket 签发短时、一次性的连接票据。
 *
 * <p>浏览器不能安全保存智谱 API Key，因此语音连接先通过普通 HTTP
 * 请求取得票据，再由服务端代理连接上游实时 ASR。</p>
 */
@Service
public class CustomerSpeechSessionService {
    private static final long TTL_SECONDS = 300;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public IssuedSession issue(RequestIdentity identity, Long storeId) {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        sessions.put(ticket, new Entry(identity, storeId, expiresAt));
        return new IssuedSession(ticket, TTL_SECONDS);
    }

    /** 成功握手后立即销毁，避免票据被重复建立连接。 */
    public Session consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        Entry entry = sessions.remove(ticket);
        if (entry == null || entry.expiresAt() <= Instant.now().getEpochSecond()) return null;
        return new Session(entry.identity(), entry.storeId());
    }

    private void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Entry(RequestIdentity identity, Long storeId, long expiresAt) { }

    public record IssuedSession(String ticket, long expiresInSeconds) { }

    public record Session(RequestIdentity identity, Long storeId) { }
}
