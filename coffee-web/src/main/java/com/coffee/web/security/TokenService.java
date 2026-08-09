package com.coffee.web.security;

import com.coffee.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/** 无状态、带过期时间的 HMAC 会话令牌服务。 */
@Component
public class TokenService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private final byte[] secret;
    private final long expiresSeconds;

    public TokenService(@Value("${coffee.auth.token-secret:change-this-development-secret-before-production-2026}") String secret,
                        @Value("${coffee.auth.token-expires-seconds:28800}") long expiresSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("coffee.auth.token-secret 至少需要 32 个字符");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresSeconds = expiresSeconds;
    }

    public String issueUser(Long userId) { return issue(RequestIdentity.Kind.USER, userId, null); }
    public String issueMerchant(Long merchantId) { return issue(RequestIdentity.Kind.MERCHANT, merchantId, null); }
    public String issueGuest(String guestId) { return issue(RequestIdentity.Kind.GUEST, null, guestId); }

    private String issue(RequestIdentity.Kind kind, Long id, String guestId) {
        long expiresAt = Instant.now().getEpochSecond() + expiresSeconds;
        String subject = kind == RequestIdentity.Kind.GUEST ? guestId : String.valueOf(id);
        String payload = "v1." + kind.name() + "." + B64.encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    public RequestIdentity verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 5 || !"v1".equals(parts[0])) throw invalid();
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII), parts[4].getBytes(StandardCharsets.US_ASCII))) throw invalid();
            if (Instant.now().getEpochSecond() >= Long.parseLong(parts[3])) throw new ServiceException(401, "登录已过期，请重新登录");
            RequestIdentity.Kind kind = RequestIdentity.Kind.valueOf(parts[1]);
            String subject = new String(B64_DECODER.decode(parts[2]), StandardCharsets.UTF_8);
            return kind == RequestIdentity.Kind.GUEST
                    ? new RequestIdentity(kind, null, subject)
                    : new RequestIdentity(kind, Long.valueOf(subject), null);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化令牌服务", e);
        }
    }

    private ServiceException invalid() { return new ServiceException(401, "无效的登录凭证"); }
}
