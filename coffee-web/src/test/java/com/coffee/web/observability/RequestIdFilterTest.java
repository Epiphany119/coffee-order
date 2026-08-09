package com.coffee.web.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void shouldPropagateSafeRequestIdAndClearMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "order-20260809-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertEquals("order-20260809-0001", org.slf4j.MDC.get(RequestIdFilter.MDC_KEY)));

        assertEquals("order-20260809-0001", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertNull(org.slf4j.MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void shouldReplaceUnsafeClientRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(generated);
        assertEquals(36, generated.length());
    }
}
