package com.coffee.web.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRegistryTest {
    private final AgentToolRegistry registry = new AgentToolRegistry();

    @Test
    void exposesDifferentToolScopesForCustomerAndMerchant() {
        assertEquals(2, registry.available("customer").size());
        assertEquals(3, registry.available("merchant").size());
        assertTrue(registry.isAllowed("merchant", "operation_metrics"));
        assertFalse(registry.isAllowed("customer", "operation_metrics"));
    }

    @Test
    void everyRegisteredToolIsReadOnlyUntilApprovalFlowIsAdded() {
        assertTrue(registry.available("merchant").stream().allMatch(AgentToolRegistry.ToolDescriptor::readOnly));
        assertTrue(registry.available("customer").stream().allMatch(AgentToolRegistry.ToolDescriptor::readOnly));
    }
}
