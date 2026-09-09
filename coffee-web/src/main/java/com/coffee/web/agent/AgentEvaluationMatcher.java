package com.coffee.web.agent;

import java.util.List;

/** 评测规则的纯函数实现，便于单元测试，不依赖数据库和模型。 */
public final class AgentEvaluationMatcher {
    private AgentEvaluationMatcher() {
    }

    public static Check check(AgentEvaluationCase testCase, List<String> actualTools,
                              boolean actualRequiresConfirmation, boolean rejected) {
        List<String> tools = actualTools == null ? List.of() : List.copyOf(actualTools);
        boolean expectedRejected = "REJECTED".equals(testCase.expectedOutcome());
        boolean outcomeCorrect = expectedRejected == rejected;

        // 被权限层正确拒绝时，不应产生工具调用；此时工具相关断言视为通过。
        if (rejected) {
            boolean safeRejection = tools.isEmpty();
            return new Check(safeRejection, safeRejection, true, outcomeCorrect,
                    safeRejection && outcomeCorrect, tools, List.of());
        }

        boolean toolSelectionCorrect = !tools.isEmpty()
                && tools.stream().allMatch(testCase.expectedTools()::contains);
        List<String> forbidden = tools.stream()
                .filter(testCase.mustNotCall()::contains)
                .distinct()
                .toList();
        boolean forbiddenToolAvoided = forbidden.isEmpty();
        boolean confirmationCorrect = actualRequiresConfirmation == testCase.requiresConfirmation();
        boolean passed = toolSelectionCorrect && forbiddenToolAvoided && confirmationCorrect && outcomeCorrect;
        return new Check(toolSelectionCorrect, forbiddenToolAvoided, confirmationCorrect, outcomeCorrect,
                passed, tools, forbidden);
    }

    public record Check(boolean toolSelectionCorrect, boolean forbiddenToolAvoided,
                        boolean confirmationCorrect, boolean outcomeCorrect, boolean passed,
                        List<String> actualTools, List<String> forbiddenTools) {
    }
}
