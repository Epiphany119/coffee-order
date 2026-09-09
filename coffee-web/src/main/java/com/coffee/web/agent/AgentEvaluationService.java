package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FIKA Agent 评测执行器。
 *
 * <p>每次执行只调用当前已经存在的只读 Business Agent，不会创建订单、扣款或修改库存；
 * 评测断言和实际工具轨迹会落到 agent_eval_result，便于比较 Prompt/模型版本变化。</p>
 */
@Service
public class AgentEvaluationService {
    private static final String CASE_RESOURCE = "agent/agent_cases.jsonl";

    private final ObjectMapper json;
    private final BusinessAgentOrchestrator orchestrator;
    private final AgentRunAuditService audit;
    private final boolean enabled;
    private volatile List<AgentEvaluationCase> cachedCases;

    public AgentEvaluationService(ObjectMapper json,
                                  BusinessAgentOrchestrator orchestrator,
                                  AgentRunAuditService audit,
                                  @Value("${coffee.ai.evaluation.enabled:true}") boolean enabled) {
        this.json = json;
        this.orchestrator = orchestrator;
        this.audit = audit;
        this.enabled = enabled;
    }

    public List<AgentEvaluationCase> cases() {
        if (cachedCases == null) {
            synchronized (this) {
                if (cachedCases == null) cachedCases = loadCases();
            }
        }
        return cachedCases;
    }

    public EvaluationResult run(RequestIdentity identity, String caseId, Long storeId, String sessionId) {
        if (!enabled) throw new ServiceException(403, "Agent 评测功能已关闭");
        AgentEvaluationCase testCase = cases().stream()
                .filter(item -> item.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(404, "评测样例不存在：" + caseId));
        validateScene(identity, testCase);

        long started = System.nanoTime();
        BusinessAgentOrchestrator.AgentAnswer answer = null;
        String error = null;
        try {
            answer = orchestrator.execute(identity, testCase.scene(), storeId, sessionId, testCase.input());
        } catch (RuntimeException ex) {
            error = safeError(ex);
        }

        boolean rejected = answer == null;
        List<String> actualTools = answer == null
                ? List.of()
                : answer.tools().stream().map(BusinessAgentOrchestrator.ToolResult::name).toList();
        boolean actualConfirmation = answer != null && answer.structuredPlan().requiresConfirmation();
        AgentEvaluationMatcher.Check check = AgentEvaluationMatcher.check(
                testCase, actualTools, actualConfirmation, rejected);
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("runId", answer == null ? null : answer.runId());
        actual.put("engine", answer == null ? null : answer.engine());
        actual.put("tools", actualTools);
        actual.put("requiresConfirmation", actualConfirmation);
        actual.put("rejected", rejected);
        actual.put("forbiddenTools", check.forbiddenTools());

        audit.recordEvaluation(identity, testCase.id(), answer == null ? null : answer.runId(),
                check.toolSelectionCorrect(), check.forbiddenToolAvoided(), check.confirmationCorrect(),
                check.outcomeCorrect(), check.passed(), serialize(testCase), serialize(actual), error, latencyMs);
        return new EvaluationResult(testCase.id(), testCase.category(), testCase.scene(), check.passed(),
                check.toolSelectionCorrect(), check.forbiddenToolAvoided(), check.confirmationCorrect(),
                check.outcomeCorrect(), actualTools, check.forbiddenTools(), answer == null ? null : answer.runId(),
                answer == null ? null : answer.engine(), actualConfirmation, rejected, error, latencyMs);
    }

    /** 执行当前身份可运行的全部样例；顾客与商家样例不会混用身份。 */
    public List<EvaluationResult> runAll(RequestIdentity identity, Long storeId) {
        if (!enabled) throw new ServiceException(403, "Agent 评测功能已关闭");
        String scene = identity.kind() == RequestIdentity.Kind.MERCHANT ? "merchant" : "customer";
        return cases().stream()
                .filter(item -> scene.equals(item.scene()))
                .map(item -> run(identity, item.id(), storeId, null))
                .toList();
    }

    public Map<String, Object> summary(RequestIdentity identity) {
        if (!enabled) throw new ServiceException(403, "Agent 评测功能已关闭");
        return audit.readEvaluationSummary(identity);
    }

    private List<AgentEvaluationCase> loadCases() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(CASE_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(this::parse)
                    .toList();
        } catch (Exception ex) {
            throw new ServiceException(500, "Agent 评测集加载失败");
        }
    }

    private AgentEvaluationCase parse(String line) {
        try {
            return json.readValue(line, AgentEvaluationCase.class);
        } catch (Exception ex) {
            throw new ServiceException(500, "Agent 评测集格式错误");
        }
    }

    private void validateScene(RequestIdentity identity, AgentEvaluationCase testCase) {
        if ("merchant".equals(testCase.scene()) && identity.kind() != RequestIdentity.Kind.MERCHANT) {
            throw new ServiceException(403, "商家评测需要商家身份");
        }
        if ("customer".equals(testCase.scene())
                && identity.kind() != RequestIdentity.Kind.USER
                && identity.kind() != RequestIdentity.Kind.GUEST) {
            throw new ServiceException(403, "顾客评测需要顾客或游客身份");
        }
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
    }

    public record EvaluationResult(String caseId, String category, String scene, boolean passed,
                                   boolean toolSelectionCorrect, boolean forbiddenToolAvoided,
                                   boolean confirmationCorrect, boolean outcomeCorrect,
                                   List<String> actualTools, List<String> forbiddenTools,
                                   String runId, String engine, boolean requiresConfirmation,
                                   boolean rejected, String error, long latencyMs) {
    }
}
