package com.coffee.web.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 的结构化执行计划。
 *
 * <p>模型只负责提出计划，业务代码负责校验工具、权限和参数。计划中的工具
 * 不能携带 SQL、URL 或 Java 类名等动态执行信息。</p>
 */
public record AgentPlan(
        String intent,
        List<Step> steps,
        boolean requiresConfirmation,
        String rationale,
        String source
) {
    public AgentPlan {
        intent = intent == null || intent.isBlank() ? "general" : intent.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
        rationale = rationale == null ? "" : rationale.trim();
        source = source == null || source.isBlank() ? "unknown" : source.trim();
    }

    public List<String> toolNames() {
        return steps.stream().map(Step::tool).toList();
    }

    public record Step(String tool, Map<String, Object> arguments, String purpose) {
        public Step {
            tool = tool == null ? "" : tool.trim();
            arguments = arguments == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
            purpose = purpose == null ? "" : purpose.trim();
        }
    }
}
