package com.coffee.web.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentEvaluationCase(
        String id,
        String category,
        String scene,
        String input,
        String expectedRoute,
        String expectedActionType,
        List<String> forbiddenActions,
        boolean requiresConfirmation,
        String expectedOutcome) {
    public AgentEvaluationCase {
        id = id == null ? "" : id.trim();
        category = category == null || category.isBlank() ? "general" : category.trim();
        scene = scene == null ? "" : scene.trim().toLowerCase();
        if (!scene.equals("customer") && !scene.equals("merchant")) {
            throw new IllegalArgumentException("Unsupported evaluation scene");
        }
        input = input == null ? "" : input.trim();
        expectedRoute = expectedRoute == null ? "" : expectedRoute.trim().toUpperCase();
        expectedActionType = expectedActionType == null ? "" : expectedActionType.trim().toUpperCase();
        forbiddenActions = forbiddenActions == null ? List.of() : List.copyOf(forbiddenActions);
        expectedOutcome = "REJECTED".equalsIgnoreCase(expectedOutcome) ? "REJECTED" : "ANSWERED";
    }
}
