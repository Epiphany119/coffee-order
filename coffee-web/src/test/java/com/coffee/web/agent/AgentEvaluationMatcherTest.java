package com.coffee.web.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvaluationMatcherTest {
    @Test
    void acceptsExpectedCustomerOrderPlanWithoutCreatingAnOrder() {
        AgentEvaluationCase testCase = new AgentEvaluationCase(
                "order", "customer_order_plan", "customer", "input",
                "ORDER", "ORDER_PLAN", List.of("ORDER_CREATED", "CHARGE_PAYMENT"), true, "ANSWERED");

        AgentEvaluationMatcher.Check check = AgentEvaluationMatcher.check(
                testCase, "ORDER", "ORDER_PLAN", true, false);

        assertTrue(check.passed());
        assertTrue(check.toolSelectionCorrect());
        assertTrue(check.forbiddenToolAvoided());
    }

    @Test
    void rejectsWrongRouteForbiddenActionAndConfirmationState() {
        AgentEvaluationCase testCase = new AgentEvaluationCase(
                "safe", "customer_consult", "customer", "input",
                "CONSULT", "", List.of("ORDER_CREATED"), false, "ANSWERED");

        AgentEvaluationMatcher.Check check = AgentEvaluationMatcher.check(
                testCase, "ORDER", "ORDER_CREATED", true, false);

        assertFalse(check.passed());
        assertFalse(check.toolSelectionCorrect());
        assertFalse(check.forbiddenToolAvoided());
        assertFalse(check.confirmationCorrect());
        assertTrue(check.forbiddenTools().contains("ORDER_CREATED"));
    }

    @Test
    void acceptsExplicitUnsafeRouteRejection() {
        AgentEvaluationCase testCase = new AgentEvaluationCase(
                "unsafe", "customer_safety", "customer", "input",
                "UNSAFE", "", List.of("ORDER_CREATED", "CHARGE_PAYMENT"), false, "REJECTED");

        AgentEvaluationMatcher.Check check = AgentEvaluationMatcher.check(
                testCase, "UNSAFE", "NONE", false, true);

        assertTrue(check.passed());
        assertTrue(check.outcomeCorrect());
    }
}
