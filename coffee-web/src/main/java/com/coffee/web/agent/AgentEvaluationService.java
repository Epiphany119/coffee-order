package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.merchantagent.api.MerchantGrowthAgentService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreResponse;
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

@Service
public class AgentEvaluationService {
    private static final String CASE_RESOURCE = "agent/agent_cases.jsonl";

    private final ObjectMapper json;
    private final CustomerSupervisorAgentOrchestrator customerAgent;
    private final MerchantGrowthAgentService merchantAgent;
    private final StoreService storeService;
    private final AgentRunAuditService audit;
    private final ZhipuChatClient chatClient;
    private final boolean enabled;
    private volatile List<AgentEvaluationCase> cachedCases;

    public AgentEvaluationService(ObjectMapper json,
                                  CustomerSupervisorAgentOrchestrator customerAgent,
                                  MerchantGrowthAgentService merchantAgent,
                                  StoreService storeService,
                                  AgentRunAuditService audit,
                                  ZhipuChatClient chatClient,
                                  @Value("$" + "{coffee.ai.evaluation.enabled:true}") boolean enabled) {
        this.json = json;
        this.customerAgent = customerAgent;
        this.merchantAgent = merchantAgent;
        this.storeService = storeService;
        this.audit = audit;
        this.chatClient = chatClient;
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
        ensureEnabled();
        AgentEvaluationCase testCase = cases().stream()
                .filter(item -> item.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(404, "Evaluation case not found"));
        AgentSceneAuthorization.requireScene(identity, testCase.scene(),
                identity != null && identity.kind() == RequestIdentity.Kind.MERCHANT ? identity.id() : null);
        Long resolvedStoreId = resolveStore(identity, storeId, testCase.scene());

        long started = System.nanoTime();
        String runId = null;
        String engine = null;
        String route = null;
        String actionType = null;
        String error = null;
        boolean requiresConfirmation = false;
        boolean rejected = false;
        int toolCount = 0;
        boolean merchantRun = "merchant".equals(testCase.scene());

        if (merchantRun) {
            runId = audit.start(identity, "merchant_growth", resolvedStoreId, sessionId, testCase.input());
            chatClient.beginUsageTracking();
        }
        try {
            if (merchantRun) {
                Map<String, Object> analysis = merchantAgent.analyze(identity.id(), resolvedStoreId, testCase.input(), "", false);
                route = "ANALYZE";
                Object suggested = analysis.get("suggestedAction");
                if (suggested instanceof Map<?, ?> action) {
                    Object value = action.get("actionType");
                    actionType = value == null ? null : String.valueOf(value);
                }
                requiresConfirmation = Boolean.TRUE.equals(analysis.get("requiresConfirmation"));
                engine = String.valueOf(analysis.getOrDefault("engine", "merchant-growth-agent"));
                List<?> calls = analysis.get("toolCalls") instanceof List<?> list ? list : List.of();
                toolCount = calls.size();
                audit.recordPlanJson(runId, Map.of("route", route, "requiresConfirmation", requiresConfirmation,
                        "actionType", actionType == null ? "" : actionType), engine, null);
                for (int i = 0; i < calls.size(); i++) {
                    if (calls.get(i) instanceof Map<?, ?> call) {
                        audit.recordStep(runId, i, String.valueOf(call.get("name") == null ? "read_tool" : call.get("name")), true,
                                String.valueOf(call.get("status") == null ? "SUCCEEDED" : call.get("status")),
                                number(call.get("latencyMs")), number(call.get("resultCount")),
                                Map.of(), String.valueOf(call.get("note") == null ? "" : call.get("note")));
                    }
                }
                audit.finish(identity, runId, "SUCCEEDED",
                        String.valueOf(analysis.getOrDefault("answer", "")), null, toolCount);
            } else {
                CustomerSupervisorAgentOrchestrator.AssistantAnswer answer =
                        customerAgent.execute(identity, resolvedStoreId, sessionId, testCase.input());
                runId = answer.runId();
                route = answer.route();
                actionType = answer.action() == null ? null : answer.action().type();
                requiresConfirmation = "ORDER".equals(route) || "FEEDBACK".equals(route);
                engine = answer.engine();
                toolCount = 1;
            }
        } catch (RuntimeException ex) {
            rejected = true;
            error = safeError(ex);
            if (merchantRun && runId != null) {
                audit.finish(identity, runId, "FAILED", null, error, toolCount);
            }
        } finally {
            if (merchantRun && runId != null) {
                audit.recordModelUsage(runId, chatClient.endUsageTracking());
            }
        }

        if ("UNSAFE".equals(route)) rejected = true;
        AgentEvaluationMatcher.Check check = AgentEvaluationMatcher.check(
                testCase, route, actionType, requiresConfirmation, rejected);
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("runId", runId);
        actual.put("engine", engine);
        actual.put("route", route);
        actual.put("actionType", actionType);
        actual.put("requiresConfirmation", requiresConfirmation);
        actual.put("rejected", rejected);
        actual.put("actualActions", check.actualTools());
        actual.put("forbiddenActions", check.forbiddenTools());
        actual.put("proposalPrepared", false);
        audit.recordEvaluation(identity, testCase.id(), runId, check.toolSelectionCorrect(),
                check.forbiddenToolAvoided(), check.confirmationCorrect(), check.outcomeCorrect(),
                check.passed(), serialize(testCase), serialize(actual), error, latencyMs);
        return new EvaluationResult(testCase.id(), testCase.category(), testCase.scene(), check.passed(),
                check.toolSelectionCorrect(), check.forbiddenToolAvoided(), check.confirmationCorrect(),
                check.outcomeCorrect(), check.actualTools(), check.forbiddenTools(), runId, engine,
                requiresConfirmation, rejected, error, latencyMs);
    }

    public List<EvaluationResult> runAll(RequestIdentity identity, Long storeId) {
        ensureEnabled();
        String scene;
        if (identity != null && identity.kind() == RequestIdentity.Kind.MERCHANT) scene = "merchant";
        else if (identity != null && (identity.kind() == RequestIdentity.Kind.USER
                || identity.kind() == RequestIdentity.Kind.GUEST)) scene = "customer";
        else throw new ServiceException(403, "This identity cannot run Agent evaluations");
        Long resolvedStore = resolveStore(identity, storeId, scene);
        return cases().stream().filter(item -> scene.equals(item.scene()))
                .map(item -> run(identity, item.id(), resolvedStore, null)).toList();
    }

    public Map<String, Object> summary(RequestIdentity identity) {
        ensureEnabled();
        return audit.readEvaluationSummary(identity);
    }

    private Long resolveStore(RequestIdentity identity, Long requestedStoreId, String scene) {
        if ("customer".equals(scene)) {
            if (requestedStoreId == null || requestedStoreId <= 0) {
                throw new ServiceException(400, "A store is required for customer evaluation");
            }
            return requestedStoreId;
        }
        List<StoreResponse> stores = storeService.listByMerchant(identity.id());
        if (stores.isEmpty()) throw new ServiceException(404, "Merchant has no store");
        if (requestedStoreId == null) return stores.get(0).getStoreId();
        return stores.stream().map(StoreResponse::getStoreId).filter(requestedStoreId::equals).findFirst()
                .orElseThrow(() -> new ServiceException(403, "Store is not owned by this merchant"));
    }

    private void ensureEnabled() {
        if (!enabled) throw new ServiceException(403, "Agent evaluation is disabled");
    }

    private List<AgentEvaluationCase> loadCases() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(CASE_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(this::parse).toList();
        } catch (Exception ex) {
            throw new ServiceException(500, "Could not load Agent evaluation cases");
        }
    }

    private AgentEvaluationCase parse(String line) {
        try {
            return json.readValue(line, AgentEvaluationCase.class);
        } catch (Exception ex) {
            throw new ServiceException(500, "Invalid Agent evaluation case");
        }
    }

    private int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { return "{}"; }
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
                                  boolean rejected, String error, long latencyMs) { }
}
