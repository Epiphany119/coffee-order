package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 运行观测与审计：记录计划、工具调用、延迟和最终状态。
 *
 * <p>审计表通过独立迁移创建。迁移尚未执行时，Agent 主流程仍可降级运行，
 * 但会在日志中提示；完成迁移后无需改代码即可恢复记录。</p>
 */
@Service
public class AgentRunAuditService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunAuditService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
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
                """, runId, ownerKey(identity), scene, storeId, sessionId, question, "RUNNING", LocalDateTime.now()));
        return runId;
    }

    public void recordPlan(String runId, AgentPlan plan, String engine) {
        write("record plan", () -> jdbc.update(
                "UPDATE agent_run SET plan_json=?,engine=? WHERE run_id=?",
                serialize(plan), engine, runId));
    }

    public void recordToolCall(String runId, int sequence, BusinessAgentOrchestrator.ToolResult result) {
        write("record tool call", () -> jdbc.update("""
                INSERT INTO agent_tool_call(run_id,sequence_no,tool_name,read_only,status,latency_ms,result_count,
                                            arguments_json,note,started_at,finished_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, runId, sequence, result.name(), result.readOnly(), result.success() ? "SUCCEEDED" : "FAILED",
                result.latencyMs(), result.data().size(), serialize(result.arguments()), result.note(),
                LocalDateTime.now().minusNanos(result.latencyMs() * 1_000_000), LocalDateTime.now()));
    }

    public void finish(String runId, String status, String answer, String error, int toolCount) {
        write("finish run", () -> jdbc.update("""
                UPDATE agent_run
                SET status=?,answer=?,error_message=?,tool_count=?,finished_at=?
                WHERE run_id=?
                """, status, answer, error, toolCount, LocalDateTime.now(), runId));
    }

    public Map<String, Object> read(RequestIdentity identity, String runId) {
        if (runId == null || !runId.matches("[a-f0-9]{32}")) {
            throw new ServiceException(400, "Agent 运行编号格式无效");
        }
        if (!enabled) {
            return Map.of("runId", runId, "available", false, "message", "审计表尚未初始化");
        }
        try {
            Map<String, Object> run = jdbc.queryForMap(
                    "SELECT run_id AS runId,scene,store_id AS storeId,session_id AS sessionId,status,engine,"
                            + "tool_count AS toolCount,started_at AS startedAt,finished_at AS finishedAt "
                            + "FROM agent_run WHERE run_id=? AND owner_key=?",
                    runId, ownerKey(identity));
            List<Map<String, Object>> calls = jdbc.queryForList(
                    "SELECT sequence_no AS sequenceNo,tool_name AS toolName,read_only AS readOnly,status,"
                            + "latency_ms AS latencyMs,result_count AS resultCount,note,started_at AS startedAt,"
                            + "finished_at AS finishedAt FROM agent_tool_call WHERE run_id=? ORDER BY sequence_no",
                    runId);
            Map<String, Object> response = new LinkedHashMap<>(run);
            response.put("available", true);
            response.put("toolCalls", calls);
            return response;
        } catch (EmptyResultDataAccessException ex) {
            throw new ServiceException(404, "Agent 运行记录不存在或不属于当前身份");
        } catch (DataAccessException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("agent_run")) {
                disable("read audit");
                return Map.of("runId", runId, "available", false, "message", "审计表尚未初始化");
            }
            throw new ServiceException(500, "Agent 运行记录暂时不可用");
        }
    }

    private String ownerKey(RequestIdentity identity) {
        return identity.kind().name() + ":" + (identity.id() == null ? identity.guestId() : identity.id());
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
            disable(operation);
        }
    }

    private void disable(String operation) {
        if (enabled) {
            enabled = false;
            log.warn("Agent {} skipped: run audit migration is not available; execute V20260906_17_agent_observability.sql", operation);
        }
    }
}
