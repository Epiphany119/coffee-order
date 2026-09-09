package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 运行观测与审计：记录计划、工具调用、延迟、模型用量和最终状态。
 *
 * <p>审计写入故障不会阻断正常点单/咨询链路；执行
 * {@code sql/migrations/V20260908_24_agent_evaluation_observability.sql} 后即可
 * 获得完整的运行轨迹和评测结果。</p>
 */
@Service
public class AgentRunAuditService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunAuditService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /** 价格由部署环境注入；未配置时仍记录 token，但成本明确标记为 unpriced。 */
    @Value("${coffee.ai.observability.input-cost-per-1k:0}")
    private String inputCostPer1k = "0";
    @Value("${coffee.ai.observability.output-cost-per-1k:0}")
    private String outputCostPer1k = "0";
    @Value("${coffee.ai.observability.cost-currency:CNY}")
    private String costCurrency = "CNY";

    private volatile boolean enabled = true;

    public AgentRunAuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public String start(RequestIdentity identity, String scene, Long storeId, String sessionId, String question) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        write("start run", () -> jdbc.update("""
                INSERT INTO agent_run(run_id,owner_key,scene,store_id,session_id,question,status,started_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, runId, ownerKey(identity), scene, storeId, sessionId,
                question == null ? "" : question, "RUNNING", LocalDateTime.now()));
        return runId;
    }

    public void recordPlan(String runId, AgentPlan plan, String engine) {
        recordPlan(runId, plan, engine, null);
    }

    public void recordPlan(String runId, AgentPlan plan, String engine, String fallbackReason) {
        recordPlanJson(runId, plan, engine, fallbackReason);
    }

    /** 允许 customer-agent 记录自己的结构化方案，但不把一次性 planToken 写入审计。 */
    public void recordPlanJson(String runId, Object plan, String engine, String fallbackReason) {
        write("record plan", () -> jdbc.update(
                "UPDATE agent_run SET plan_json=?,engine=?,fallback_reason=? WHERE run_id=?",
                serialize(plan), engine, fallbackReason, runId));
    }

    public void markPlanReady(String runId) {
        write("mark plan ready", () -> jdbc.update(
                "UPDATE agent_run SET status='PLAN_READY' WHERE run_id=? AND status='RUNNING'", runId));
    }

    public void recordToolCall(String runId, int sequence, BusinessAgentOrchestrator.ToolResult result) {
        recordStep(runId, sequence, result.name(), result.readOnly(),
                result.success() ? "SUCCEEDED" : "FAILED", result.latencyMs(), result.data().size(),
                result.arguments(), result.note());
    }

    /** 记录不属于 BusinessAgentOrchestrator 的受控步骤，例如 customer-agent 下单。 */
    public void recordStep(String runId, int sequence, String stepName, boolean readOnly, String status,
                           long latencyMs, int resultCount, Map<String, Object> arguments, String note) {
        write("record step", () -> insertStep(runId, sequence, stepName, readOnly, status, latencyMs,
                resultCount, arguments, note));
    }

    /** 对浏览器带回的 runId 做身份隔离后再写步骤，避免串改其他用户的轨迹。 */
    public void recordOwnedStep(RequestIdentity identity, String runId, int sequence, String stepName,
                                boolean readOnly, String status, long latencyMs, int resultCount,
                                Map<String, Object> arguments, String note) {
        if (!isValidRunId(runId)) return;
        write("record owned step", () -> {
            if (!belongsTo(identity, runId)) return;
            int actualSequence = nextSequence(runId, sequence);
            insertStep(runId, actualSequence, stepName, readOnly, status, latencyMs, resultCount, arguments, note);
        });
    }

    public void recordModelUsage(String runId, ZhipuChatClient.UsageSnapshot usage) {
        if (usage == null) return;
        BigDecimal inputPrice = decimal(inputCostPer1k);
        BigDecimal outputPrice = decimal(outputCostPer1k);
        BigDecimal cost = inputPrice.multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(outputPrice.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        String source = usage.estimated()
                ? (inputPrice.signum() == 0 && outputPrice.signum() == 0
                ? "estimated_chars_unpriced" : "estimated_chars")
                : "provider_usage";
        write("record model usage", () -> jdbc.update("""
                UPDATE agent_run
                SET model_name=?,model_call_count=?,input_tokens=?,output_tokens=?,total_tokens=?,
                    model_cost=?,model_cost_currency=?,model_cost_source=?
                WHERE run_id=?
                """, usage.model(), usage.modelCalls(), usage.inputTokens(), usage.outputTokens(),
                usage.totalTokens(), cost, safeCurrency(), source, runId));
    }

    public void recordOrderResult(RequestIdentity identity, String runId, boolean success, Long orderId) {
        if (!isValidRunId(runId)) return;
        write("record order result", () -> jdbc.update("""
                UPDATE agent_run SET order_success=?,order_id=? WHERE run_id=? AND owner_key=?
                """, success, orderId, runId, ownerKey(identity)));
    }

    public void finish(RequestIdentity identity, String runId, String status, String answer, String error,
                       int toolCount) {
        finish(identity, runId, status, answer, error, toolCount, null, null);
    }

    public void finish(RequestIdentity identity, String runId, String status, String answer, String error,
                       int toolCount, Boolean orderSuccess, Long orderId) {
        if (!isValidRunId(runId)) return;
        write("finish run", () -> jdbc.update("""
                UPDATE agent_run
                SET status=?,answer=?,error_message=?,tool_count=GREATEST(tool_count,?),
                    order_success=COALESCE(?,order_success),order_id=COALESCE(?,order_id),finished_at=?
                WHERE run_id=? AND owner_key=?
                """, status, answer, error, toolCount, orderSuccess, orderId, LocalDateTime.now(),
                runId, ownerKey(identity)));
    }

    /** 仅供内部已生成 runId 的链路使用；对外 HTTP 入口使用带 identity 的重载。 */
    public void finish(String runId, String status, String answer, String error, int toolCount) {
        if (!isValidRunId(runId)) return;
        write("finish run", () -> jdbc.update("""
                UPDATE agent_run
                SET status=?,answer=?,error_message=?,tool_count=GREATEST(tool_count,?),finished_at=?
                WHERE run_id=?
                """, status, answer, error, toolCount, LocalDateTime.now(), runId));
    }

    public void recordEvaluation(RequestIdentity identity, String caseId, String runId,
                                 boolean toolSelectionCorrect, boolean forbiddenToolAvoided,
                                 boolean confirmationCorrect, boolean outcomeCorrect, boolean passed,
                                 String expectedJson,
                                 String actualJson, String error, long latencyMs) {
        write("record evaluation", () -> jdbc.update("""
                INSERT INTO agent_eval_result(
                    case_id,run_id,owner_key,tool_selection_correct,forbidden_tool_avoided,
                    confirmation_correct,outcome_correct,passed,expected_json,actual_json,error_message,latency_ms,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, caseId, runId, ownerKey(identity), toolSelectionCorrect, forbiddenToolAvoided,
                confirmationCorrect, outcomeCorrect, passed, expectedJson, actualJson, error, latencyMs, LocalDateTime.now()));
    }

    public Map<String, Object> readEvaluationSummary(RequestIdentity identity) {
        if (!enabled) return Map.of("available", false, "message", "评测表尚未初始化");
        try {
            Map<String, Object> summary = jdbc.queryForMap("""
                    SELECT COUNT(*) AS totalCases,
                           COALESCE(SUM(passed),0) AS passedCases,
                           COALESCE(SUM(tool_selection_correct),0) AS toolSelectionPassed,
                           COALESCE(SUM(forbidden_tool_avoided),0) AS forbiddenToolPassed,
                           COALESCE(SUM(confirmation_correct),0) AS confirmationPassed,
                           COALESCE(SUM(outcome_correct),0) AS outcomePassed,
                           COALESCE(AVG(latency_ms),0) AS averageLatencyMs
                    FROM agent_eval_result WHERE owner_key=?
                    """, ownerKey(identity));
            Map<String, Object> result = new LinkedHashMap<>(summary);
            result.put("available", true);
            return result;
        } catch (DataAccessException ex) {
            if (isSchemaUnavailable(ex)) {
                disable("read evaluation summary");
                return Map.of("available", false, "message", "评测表尚未初始化");
            }
            throw new ServiceException(500, "Agent 评测摘要暂时不可用");
        }
    }

    public Map<String, Object> read(RequestIdentity identity, String runId) {
        if (!isValidRunId(runId)) throw new ServiceException(400, "Agent 运行编号格式无效");
        if (!enabled) {
            return Map.of("runId", runId, "available", false, "message", "审计表尚未初始化");
        }
        try {
            Map<String, Object> run = jdbc.queryForMap("""
                    SELECT run_id AS runId,scene,store_id AS storeId,session_id AS sessionId,question,status,engine,
                           plan_json AS planJson,answer,error_message AS errorMessage,fallback_reason AS fallbackReason,
                           tool_count AS toolCount,model_name AS modelName,model_call_count AS modelCallCount,
                           input_tokens AS inputTokens,output_tokens AS outputTokens,total_tokens AS totalTokens,
                           model_cost AS modelCost,model_cost_currency AS modelCostCurrency,
                           model_cost_source AS modelCostSource,order_success AS orderSuccess,order_id AS orderId,
                           started_at AS startedAt,finished_at AS finishedAt
                    FROM agent_run WHERE run_id=? AND owner_key=?
                    """, runId, ownerKey(identity));
            List<Map<String, Object>> calls = jdbc.queryForList("""
                    SELECT sequence_no AS sequenceNo,tool_name AS toolName,read_only AS readOnly,status,
                           latency_ms AS latencyMs,result_count AS resultCount,arguments_json AS argumentsJson,note,
                           started_at AS startedAt,finished_at AS finishedAt
                    FROM agent_tool_call WHERE run_id=? ORDER BY sequence_no
                    """, runId);
            Map<String, Object> response = new LinkedHashMap<>(run);
            response.put("available", true);
            response.put("toolCalls", calls);
            return response;
        } catch (EmptyResultDataAccessException ex) {
            throw new ServiceException(404, "Agent 运行记录不存在或不属于当前身份");
        } catch (DataAccessException ex) {
            if (isSchemaUnavailable(ex)) {
                disable("read audit");
                return Map.of("runId", runId, "available", false, "message", "审计表尚未初始化");
            }
            throw new ServiceException(500, "Agent 运行记录暂时不可用");
        }
    }

    public static boolean isValidRunId(String runId) {
        return runId != null && runId.matches("[a-f0-9]{32}");
    }

    String ownerKey(RequestIdentity identity) {
        return identity.kind().name() + ":" + (identity.id() == null ? identity.guestId() : identity.id());
    }

    private void insertStep(String runId, int sequence, String stepName, boolean readOnly, String status,
                            long latencyMs, int resultCount, Map<String, Object> arguments, String note) {
        LocalDateTime finished = LocalDateTime.now();
        LocalDateTime started = finished.minusNanos(Math.max(0, latencyMs) * 1_000_000);
        jdbc.update("""
                INSERT INTO agent_tool_call(run_id,sequence_no,tool_name,read_only,status,latency_ms,result_count,
                                            arguments_json,note,started_at,finished_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    tool_name=VALUES(tool_name),read_only=VALUES(read_only),status=VALUES(status),
                    latency_ms=VALUES(latency_ms),result_count=VALUES(result_count),
                    arguments_json=VALUES(arguments_json),note=VALUES(note),
                    started_at=VALUES(started_at),finished_at=VALUES(finished_at)
                """, runId, sequence, stepName, readOnly, status == null ? "UNKNOWN" : status,
                Math.max(0, latencyMs), Math.max(0, resultCount), serialize(arguments == null ? Map.of() : arguments),
                clip(note), started, finished);
        jdbc.update("UPDATE agent_run SET tool_count=GREATEST(tool_count,?) WHERE run_id=?",
                sequence + 1, runId);
    }

    /**
     * 同一个 customer-agent 方案可能被用户重复确认；保留每次确认的轨迹，避免
     * (run_id, sequence_no) 唯一键冲突让后续所有审计都被降级关闭。
     */
    private int nextSequence(String runId, int requestedSequence) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no) + 1, 0) FROM agent_tool_call WHERE run_id=?",
                Integer.class, runId);
        return Math.max(Math.max(0, requestedSequence), next == null ? 0 : next);
    }

    private boolean belongsTo(RequestIdentity identity, String runId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM agent_run WHERE run_id=? AND owner_key=?",
                Integer.class, runId, ownerKey(identity));
        return count != null && count > 0;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0" : value).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String safeCurrency() {
        return costCurrency == null || costCurrency.isBlank() ? "CNY" : costCurrency.trim().substring(0,
                Math.min(8, costCurrency.trim().length()));
    }

    private String clip(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void write(String operation, Runnable action) {
        if (!enabled) return;
        try {
            action.run();
        } catch (DataAccessException ex) {
            if (isSchemaUnavailable(ex)) {
                disable(operation);
            } else {
                log.warn("Agent {} audit write skipped temporarily: {}",
                        operation, ex.getClass().getSimpleName());
            }
        }
    }

    private boolean isSchemaUnavailable(DataAccessException ex) {
        String message = ex.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("agent_run") || lower.contains("agent_tool_call") || lower.contains("agent_eval_result")
                || lower.contains("unknown column") || lower.contains("table") && lower.contains("doesn't exist");
    }

    private void disable(String operation) {
        if (enabled) {
            enabled = false;
            log.warn("Agent {} skipped: execute V20260908_24_agent_evaluation_observability.sql", operation);
        }
    }
}
