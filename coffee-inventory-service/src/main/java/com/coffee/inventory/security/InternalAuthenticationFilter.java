package com.coffee.inventory.security;

import com.coffee.common.internal.InternalAuthHeaders;
import com.coffee.common.internal.InternalRequestSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/** Enforces signed service identity on every /internal/** request. */
public class InternalAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");
    private final InternalAuthProperties properties;
    private final InternalAuthReplayStore replayStore;

    public InternalAuthenticationFilter(InternalAuthProperties properties, InternalAuthReplayStore replayStore) {
        this.properties = properties;
        this.replayStore = replayStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/") || !properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > properties.getMaxBodyBytes()) {
            reject(response, "internal request body is too large");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(properties.getMaxBodyBytes() + 1);
        if (body.length > properties.getMaxBodyBytes()) {
            reject(response, "internal request body is too large");
            return;
        }
        String serviceName = request.getHeader(InternalAuthHeaders.SERVICE_NAME);
        String keyId = request.getHeader(InternalAuthHeaders.KEY_ID);
        String timestamp = request.getHeader(InternalAuthHeaders.TIMESTAMP);
        String nonce = request.getHeader(InternalAuthHeaders.NONCE);
        String contentHash = request.getHeader(InternalAuthHeaders.CONTENT_SHA256);
        String signature = request.getHeader(InternalAuthHeaders.SIGNATURE);

        if (isBlank(serviceName) || isBlank(keyId) || isBlank(timestamp) || isBlank(nonce)
                || isBlank(contentHash) || isBlank(signature)) {
            reject(response, "missing internal authentication headers");
            return;
        }

        String secret = properties.getKeys().get(keyId);
        if (secret == null || secret.length() < 32 || !NONCE_PATTERN.matcher(nonce).matches()
                || !SHA256_PATTERN.matcher(contentHash).matches()
                || !serviceName.equals(keyIdToService(keyId))) {
            reject(response, "unknown internal service key");
            return;
        }

        long requestEpoch;
        try {
            requestEpoch = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            reject(response, "invalid internal authentication timestamp");
            return;
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - requestEpoch) > properties.getClockSkewSeconds()) {
            reject(response, "expired internal authentication timestamp");
            return;
        }

        String actualBodyHash = InternalRequestSigner.contentSha256(body);
        if (!InternalRequestSigner.constantTimeEquals(actualBodyHash, contentHash)) {
            reject(response, "internal request body mismatch");
            return;
        }

        String expected = InternalRequestSigner.sign(request.getMethod(), request.getRequestURI(), timestamp, nonce,
                body, serviceName, secret);
        if (!InternalRequestSigner.constantTimeEquals(expected, signature)) {
            reject(response, "invalid internal request signature");
            return;
        }
        if (!replayStore.claim(serviceName, nonce, Duration.ofSeconds(properties.getClockSkewSeconds()))) {
            reject(response, "replayed internal request");
            return;
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    /** keyId format is service-name-v1, allowing key rotation by version. */
    private String keyIdToService(String keyId) {
        int versionSeparator = keyId.lastIndexOf("-v");
        return versionSeparator > 0 ? keyId.substring(0, versionSeparator) : keyId;
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
    }
}
