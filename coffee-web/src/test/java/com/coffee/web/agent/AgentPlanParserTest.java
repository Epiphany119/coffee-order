package com.coffee.web.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanParserTest {
    private final AgentPlanParser parser = new AgentPlanParser(new ObjectMapper(), new AgentToolRegistry());

    @Test
    void acceptsOnlyKnownReadOnlyTools() {
        var plan = parser.parse("""
                {"intent":"merchant_operation_diagnosis","steps":[
                  {"tool":"operation_metrics","arguments":{"range":"today"},"purpose":"读取今日经营指标"}
                ],"requiresConfirmation":false,"rationale":"先看事实"}
                """, "merchant");

        assertTrue(plan.isPresent());
        assertEquals("operation_metrics", plan.orElseThrow().steps().get(0).tool());
        assertEquals("today", plan.orElseThrow().steps().get(0).arguments().get("range"));
    }

    @Test
    void acceptsJsonInsideMarkdownFence() {
        var plan = parser.parse("""
                ```json
                {"intent":"menu_discovery","steps":[{"tool":"menu_query","purpose":"查菜单"}]}
                ```
                """, "customer");

        assertTrue(plan.isPresent());
        assertEquals("menu_query", plan.orElseThrow().toolNames().get(0));
    }

    @Test
    void rejectsUnknownWriteToolAndCrossSceneTool() {
        assertFalse(parser.parse("{\"steps\":[{\"tool\":\"execute_campaign\"}]}", "merchant").isPresent());
        assertFalse(parser.parse("{\"steps\":[{\"tool\":\"operation_metrics\"}]}", "customer").isPresent());
    }

    @Test
    void rejectsDuplicateStepsAndTooManyArguments() {
        assertFalse(parser.parse("{\"steps\":[{\"tool\":\"menu_query\"},{\"tool\":\"menu_query\"}]}", "customer").isPresent());
        assertFalse(parser.parse("{\"steps\":[{\"tool\":\"menu_query\",\"arguments\":{\"x\":\""
                + "a".repeat(700) + "\"}}]}", "customer").isPresent());
    }

    @Test
    void rejectsArgumentsOutsideToolSchema() {
        assertFalse(parser.parse("{\"steps\":[{\"tool\":\"menu_query\",\"arguments\":{"
                + "\"query\":\"拿铁\",\"sql\":\"drop table menu\"}}]}", "customer").isPresent());
    }
}
