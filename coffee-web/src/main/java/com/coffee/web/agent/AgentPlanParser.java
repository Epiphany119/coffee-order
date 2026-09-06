package com.coffee.web.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 把 LLM 输出收敛成可执行的、仅包含白名单只读工具的计划。 */
@Component
public class AgentPlanParser {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanParser.class);
    private static final int MAX_STEPS = 4;
    private static final int MAX_ARGUMENT_LENGTH = 600;

    private final ObjectMapper json;
    private final AgentToolRegistry registry;

    public AgentPlanParser(ObjectMapper json, AgentToolRegistry registry) {
        this.json = json;
        this.registry = registry;
    }

    public Optional<AgentPlan> parse(String raw, String scene) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            JsonNode root = json.readTree(stripMarkdownFence(raw));
            if (root == null || !root.isObject()) return Optional.empty();
            JsonNode stepsNode = root.get("steps");
            if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty() || stepsNode.size() > MAX_STEPS) {
                return Optional.empty();
            }

            List<AgentPlan.Step> steps = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode stepNode : stepsNode) {
                if (!stepNode.isObject()) return Optional.empty();
                String tool = text(stepNode, "tool");
                if (!registry.isAllowed(scene, tool) || !registry.isReadOnly(tool) || !seen.add(tool)) {
                    return Optional.empty();
                }
                JsonNode argumentsNode = stepNode.get("arguments");
                Map<String, Object> arguments = argumentsNode == null || argumentsNode.isNull()
                        ? Map.of()
                        : json.convertValue(argumentsNode, new TypeReference<>() { });
                if (!registry.argumentsAllowed(scene, tool, arguments)) return Optional.empty();
                if (arguments.toString().length() > MAX_ARGUMENT_LENGTH) return Optional.empty();
                steps.add(new AgentPlan.Step(tool, arguments, text(stepNode, "purpose")));
            }

            return Optional.of(new AgentPlan(
                    text(root, "intent"),
                    steps,
                    root.path("requiresConfirmation").asBoolean(false),
                    text(root, "rationale"),
                    "llm-structured-plan"));
        } catch (Exception ex) {
            log.debug("Agent plan parsing rejected model output: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private String stripMarkdownFence(String raw) {
        String value = raw.trim();
        if (!value.startsWith("```")) return value;
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) return value;
        return value.substring(firstLineEnd + 1, lastFence).trim();
    }
}
