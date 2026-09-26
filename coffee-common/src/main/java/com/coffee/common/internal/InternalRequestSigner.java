package com.coffee.common.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates the canonical HMAC signature used by internal HTTP calls. */
public final class InternalRequestSigner {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private InternalRequestSigner() {
    }

    public static String contentSha256(byte[] body) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash internal request body", ex);
        }
    }

    public static String canonicalRequest(String method, String path, String timestamp,
                                          String nonce, String contentSha256, String serviceName) {
        return String.join("\n", method, path, timestamp, nonce, contentSha256, serviceName);
    }

    public static String sign(String method, String path, String timestamp, String nonce,
                              byte[] body, String serviceName, String secret) {
        String bodyHash = contentSha256(body);
        String canonical = canonicalRequest(method, path, timestamp, nonce, bodyHash, serviceName);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create internal request signature", ex);
        }
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    public static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Builds headers for a client request. The caller must send the exact same body bytes. */
    public static Map<String, String> headers(String method, String path, byte[] body,
                                               String serviceName, String keyId, String secret) {
        return headers(method, path, body, serviceName, keyId, secret, Clock.systemUTC());
    }

    public static Map<String, String> headers(String method, String path, byte[] body,
                                               String serviceName, String keyId, String secret, Clock clock) {
        String timestamp = Long.toString(Instant.now(clock).getEpochSecond());
        String nonce = newNonce();
        String bodyHash = contentSha256(body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(InternalAuthHeaders.SERVICE_NAME, serviceName);
        headers.put(InternalAuthHeaders.KEY_ID, keyId);
        headers.put(InternalAuthHeaders.TIMESTAMP, timestamp);
        headers.put(InternalAuthHeaders.NONCE, nonce);
        headers.put(InternalAuthHeaders.CONTENT_SHA256, bodyHash);
        headers.put(InternalAuthHeaders.SIGNATURE,
                sign(method, path, timestamp, nonce, body, serviceName, secret));
        return headers;
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
