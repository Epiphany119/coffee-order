package com.coffee.web.security;

import com.coffee.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
class AiRateLimitInterceptorTest {

    @AfterEach
    void clearIdentity() {
        RequestIdentityHolder.clear();
    }

    @Test
    void localFallbackReturns429AfterTheConfiguredWindowQuota() {
        AiRateLimitProperties properties = new AiRateLimitProperties();
        properties.setRedisEnabled(false);
        properties.setWindowSeconds(60);
        properties.setCustomerPlanRequests(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, null,
                Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC));

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/api/customer-agent/plan");
        firstRequest.setRemoteAddr("127.0.0.9");
        assertTrue(interceptor.preHandle(firstRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/api/customer-agent/plan");
        secondRequest.setRemoteAddr("127.0.0.9");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(secondRequest, rejectedResponse, new Object()));

        assertEquals(429, exception.getCode());
        assertEquals("60", rejectedResponse.getHeader("Retry-After"));
    }

    @Test
    void quotaIsSeparatedByAuthenticatedIdentity() {
        AiRateLimitProperties properties = new AiRateLimitProperties();
        properties.setRedisEnabled(false);
        properties.setCustomerPlanRequests(1);
        AiRateLimitInterceptor interceptor = new AiRateLimitInterceptor(properties, null,
                Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/customer-agent/plan");

        RequestIdentityHolder.set(new RequestIdentity(RequestIdentity.Kind.USER, 1L, null));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        RequestIdentityHolder.set(new RequestIdentity(RequestIdentity.Kind.USER, 2L, null));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
