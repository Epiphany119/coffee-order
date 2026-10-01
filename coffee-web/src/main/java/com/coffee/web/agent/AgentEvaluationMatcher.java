package com.coffee.web.agent;

import java.util.ArrayList;
import java.util.List;

public final class AgentEvaluationMatcher {
    private AgentEvaluationMatcher() { }

    public static Check check(AgentEvaluationCase testCase, String actualRoute, String actualActionType,
                              boolean actualRequiresConfirmation, boolean rejected) {
        String route = actualRoute == null ? "" : actualRoute.trim().toUpperCase();
        String actionType = actualActionType == null ? "" : actualActionType.trim().toUpperCase();
        boolean routeCorrect = !testCase.expectedRoute().isBlank()
                && testCase.expectedRoute().equals(route);
        boolean actionCorrect = testCase.expectedActionType().isBlank()
                || testCase.expectedActionType().equals(actionType);
        boolean outcomeCorrect = "REJECTED".equals(testCase.expectedOutcome()) == rejected;

        List<String> actual = new ArrayList<>();
        if (!route.isBlank()) actual.add(route);
        if (!actionType.isBlank() && !actionType.equals("NONE")) actual.add(actionType);
        List<String> forbidden = actual.stream()
                .filter(action -> testCase.forbiddenActions().stream()
                        .anyMatch(item -> item.equalsIgnoreCase(action)))
                .distinct().toList();

        boolean confirmationCorrect = actualRequiresConfirmation == testCase.requiresConfirmation();
        boolean selectionCorrect = routeCorrect && actionCorrect;
        boolean forbiddenAvoided = forbidden.isEmpty();
        boolean passed = selectionCorrect && forbiddenAvoided && confirmationCorrect && outcomeCorrect;
        return new Check(selectionCorrect, forbiddenAvoided, confirmationCorrect, outcomeCorrect,
                passed, List.copyOf(actual), forbidden);
    }

    public record Check(boolean toolSelectionCorrect, boolean forbiddenToolAvoided,
                        boolean confirmationCorrect, boolean outcomeCorrect, boolean passed,
                        List<String> actualTools, List<String> forbiddenTools) { }
}
