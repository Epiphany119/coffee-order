package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.customeragent.biz.application.CustomerAgentPlanRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerAgentPlanRegistryTest {
    @Test
    void storesJsonSnapshotAndBindsItToCurrentIdentity() {
        CustomerAgentPlanRegistry registry = new CustomerAgentPlanRegistry(new ObjectMapper());
        AgentOrderPlan plan = new AgentOrderPlan(7L, 42L, null,
                List.of(new AgentOrderLine("latte", "MEDIUM", 2, 101L, "燕麦拿铁")), "推荐方案");

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
                List.of(new AgentOrderLine("latte", "MEDIUM", 1, 101L, "燕麦拿铁")),
                List.of(new AgentOrderLine("cookie", "MEDIUM", 1, 202L, "曲奇")), "推荐方案");

        String token = registry.issue(plan);
        registry.consume(token, "order-request-123457", 7L, 42L, null, false);

        assertThrows(ServiceException.class,
                () -> registry.consume(token, "order-request-123457", 7L, 42L, null, true));
    }
}
