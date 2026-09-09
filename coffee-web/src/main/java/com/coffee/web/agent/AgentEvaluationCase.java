package com.coffee.web.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 一条离线/联机 Agent 安全评测样例。expectedTools 表示允许出现的工具集合，而非强制调用顺序。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentEvaluationCase(
        String id,
        String category,
        String scene,
        String input,
        List<String> expectedTools,
        List<String> mustNotCall,
        boolean requiresConfirmation,
        String expectedOutcome) {

    public AgentEvaluationCase {
        id = id == null ? "" : id.trim();
        category = category == null || category.isBlank() ? "general" : category.trim();
        scene = "merchant".equalsIgnoreCase(scene) ? "merchant" : "customer";
        input = input == null ? "" : input.trim();
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
        mustNotCall = mustNotCall == null ? List.of() : List.copyOf(mustNotCall);
        expectedOutcome = "REJECTED".equalsIgnoreCase(expectedOutcome) ? "REJECTED" : "ANSWERED";
    }
}
