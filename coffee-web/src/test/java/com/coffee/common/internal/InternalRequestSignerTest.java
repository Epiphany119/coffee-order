package com.coffee.common.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InternalRequestSignerTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_758_000_000L), ZoneOffset.UTC);

    @Test
    void signsTheExactRequestBodyAndChangesWhenBodyChanges() {
        byte[] body = "{\"skuId\":7,\"quantity\":2}".getBytes();
        String first = InternalRequestSigner.sign("POST", "/internal/inventory/reserve",
                "1758000000", "nonce-1", body, "coffee-order", SECRET);
        String same = InternalRequestSigner.sign("POST", "/internal/inventory/reserve",
                "1758000000", "nonce-1", body, "coffee-order", SECRET);
        String changed = InternalRequestSigner.sign("POST", "/internal/inventory/reserve",
                "1758000000", "nonce-1", "{\"skuId\":8}".getBytes(), "coffee-order", SECRET);

        assertEquals(first, same);
        assertNotEquals(first, changed);
        assertEquals(64, first.length());
        assertTrue(InternalRequestSigner.constantTimeEquals(first, same));
        assertFalse(InternalRequestSigner.constantTimeEquals(first, changed));
    }

    @Test
    void generatedHeadersUseTheSameBodyDigestAndTimestamp() {
        byte[] body = "payload".getBytes();
        Map<String, String> headers = InternalRequestSigner.headers(
                "PUT", "/internal/inventory/stock", body, "coffee-order", "key-1", SECRET, CLOCK);

        assertEquals("coffee-order", headers.get(InternalAuthHeaders.SERVICE_NAME));
        assertEquals("key-1", headers.get(InternalAuthHeaders.KEY_ID));
        assertEquals("1758000000", headers.get(InternalAuthHeaders.TIMESTAMP));
        assertEquals(32, headers.get(InternalAuthHeaders.NONCE).length());
        assertEquals(InternalRequestSigner.contentSha256(body), headers.get(InternalAuthHeaders.CONTENT_SHA256));
        assertEquals(64, headers.get(InternalAuthHeaders.SIGNATURE).length());
    }

    @Test
    void rejectsWeakClientCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> new InternalAuthClientInterceptor("coffee-order", "key-1", "too-short"));
    }
}
