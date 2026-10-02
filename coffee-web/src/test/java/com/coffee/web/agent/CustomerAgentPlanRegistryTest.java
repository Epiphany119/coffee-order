package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.customeragent.biz.application.CustomerAgentPlanRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerAgentPlanRegistryTest {
    @Test
    void storesJsonSnapshotAndBindsItToCurrentIdentity() {
        CustomerAgentPlanRegistry registry = new CustomerAgentPlanRegistry(new ObjectMapper());
        AgentOrderPlan plan = new AgentOrderPlan(7L, 42L, null,
                List.of(new AgentOrderLine("latte", "MEDIUM", 2, 101L, "燕麦拿铁", 32.0)), "推荐方案");

        String token = registry.issue(plan);
        AgentOrderPlan consumed = registry.consume(token, "order-request-123456", 7L, 42L, null, false);

        assertEquals(plan, consumed);
        assertThrows(ServiceException.class,
                () -> registry.consume(token, "order-request-123456", 7L, 43L, null, false));
        assertThrows(ServiceException.class,
                () -> registry.consume(token, "another-request-123456", 7L, 42L, null, false));
    }

    @Test
    void bindsAddOnChoiceToTheIdempotencyClaim() {
        CustomerAgentPlanRegistry registry = new CustomerAgentPlanRegistry(new ObjectMapper());
        AgentOrderPlan plan = new AgentOrderPlan(7L, 42L, null,
                List.of(new AgentOrderLine("latte", "MEDIUM", 1, 101L, "燕麦拿铁", 32.0)),
                List.of(new AgentOrderLine("cookie", "MEDIUM", 1, 202L, "曲奇", 12.0)), "推荐方案");

        String token = registry.issue(plan);
        registry.consume(token, "order-request-123457", 7L, 42L, null, false);

        assertThrows(ServiceException.class,
                () -> registry.consume(token, "order-request-123457", 7L, 42L, null, true));
    }

    @Test
    void refusesToStartWithRedisEnabledButNoRedisTemplate() {
        assertThrows(IllegalStateException.class,
                () -> new CustomerAgentPlanRegistry(new ObjectMapper(), null, true));
    }

    @Test
    void failsClosedWhenRedisCannotStorePlan() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        CustomerAgentPlanRegistry registry = new CustomerAgentPlanRegistry(new ObjectMapper(), provider, true);
        AgentOrderPlan plan = new AgentOrderPlan(7L, 42L, null,
                List.of(new AgentOrderLine("latte", "MEDIUM", 1, 101L, "燕麦拿铁", 32.0)), "推荐方案");

        ServiceException error = assertThrows(ServiceException.class, () -> registry.issue(plan));

        assertEquals(503, error.getCode());
    }
}
