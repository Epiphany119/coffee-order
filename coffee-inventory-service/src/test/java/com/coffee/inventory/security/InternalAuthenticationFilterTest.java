package com.coffee.inventory.security;

import com.coffee.common.internal.InternalRequestSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InternalAuthenticationFilterTest {

    private static final String SERVICE = "coffee-order";
    private static final String KEY_ID = "coffee-order-v1";
    private static final String SECRET = "01234567890123456789012345678901";
    private InternalAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setClockSkewSeconds(300);
        properties.setMaxBodyBytes(1024);
        properties.setKeys(Map.of(KEY_ID, SECRET));
        filter = new InternalAuthenticationFilter(properties, new InMemoryInternalAuthReplayStore());
    }

    @Test
    void acceptsSignedRequestAndRejectsTheSameNonceOnReplay() throws Exception {
        byte[] body = "{\"skuId\":7}".getBytes();
        Map<String, String> headers = headers(body);
        AtomicBoolean chainCalled = new AtomicBoolean();
        var chain = (jakarta.servlet.FilterChain) (request, response) -> chainCalled.set(true);

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(request(body, headers), firstResponse, chain);
        assertTrue(chainCalled.get());
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        filter.doFilter(request(body, headers), replayResponse, chain);
        assertEquals(401, replayResponse.getStatus());
        assertNotNull(replayResponse.getErrorMessage());
    }

    @Test
    void rejectsBodyTamperingBeforeEnteringTheChain() throws Exception {
        byte[] signedBody = "{\"skuId\":7}".getBytes();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request("{\"skuId\":8}".getBytes(), headers(signedBody)), response,
                (req, res) -> chainCalled.set(true));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
    }

    private Map<String, String> headers(byte[] body) {
        return InternalRequestSigner.headers("POST", "/internal/inventory/reserve", body,
                SERVICE, KEY_ID, SECRET);
    }

    private MockHttpServletRequest request(byte[] body, Map<String, String> headers) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/inventory/reserve");
        request.setContent(body);
        headers.forEach(request::addHeader);
        return request;
    }
}
