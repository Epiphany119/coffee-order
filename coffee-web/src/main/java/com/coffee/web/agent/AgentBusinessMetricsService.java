package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only business metrics for merchant Agent operations. */
@Service
public class AgentBusinessMetricsService {
    private final JdbcTemplate jdbc;

    public AgentBusinessMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summarize(RequestIdentity identity, Long storeId, int requestedDays) {
        if (identity == null || identity.kind() != RequestIdentity.Kind.MERCHANT || identity.id() == null) {
            throw new ServiceException(403, "需要商家身份");
        }
        if (storeId == null || storeId <= 0) throw new ServiceException(400, "请选择门店");
        int days = Math.max(1, Math.min(90, requestedDays));
        try {
            Map<String, Object> runs = jdbc.queryForMap("""
                    SELECT COUNT(*) AS runCount,
                           COALESCE(SUM(status='SUCCEEDED'),0) AS succeededRuns,
                           COALESCE(SUM(status='FAILED'),0) AS failedRuns,
                           COALESCE(SUM(fallback_reason IS NOT NULL AND fallback_reason<>''),0) AS fallbackRuns,
                           COALESCE(SUM(order_success IS NOT NULL),0) AS orderAttempts,
                           COALESCE(SUM(order_success=1),0) AS successfulOrders,
                           COALESCE(SUM(input_tokens),0) AS inputTokens,
                           COALESCE(SUM(output_tokens),0) AS outputTokens,
                           COALESCE(SUM(total_tokens),0) AS totalTokens,
                           COALESCE(SUM(model_cost),0) AS totalModelCost,
                           COALESCE(AVG(CASE WHEN finished_at IS NOT NULL
                               THEN TIMESTAMPDIFF(MICROSECOND,started_at,finished_at)/1000.0 END),0) AS averageLatencyMs
                    FROM agent_run
                    WHERE store_id=?
                      AND started_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    """, storeId, days);
            Map<String, Object> tools = jdbc.queryForMap("""
                    SELECT COUNT(*) AS toolCalls,
                           COALESCE(SUM(tool_name='knowledge_retrieve'),0) AS knowledgeCalls,
                           COALESCE(SUM(tool_name='knowledge_retrieve' AND result_count>0),0) AS sourcedKnowledgeCalls
                    FROM agent_tool_call c
                    JOIN agent_run r ON r.run_id=c.run_id
                    WHERE r.store_id=?
                      AND r.started_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    """, storeId, days);
            Map<String, Object> payments = jdbc.queryForMap("""
                    SELECT COUNT(*) AS mockPaymentOrders,
                           COALESCE(SUM(p.status='PAID'),0) AS mockPaymentPaid,
                           COALESCE(SUM(p.status='REFUNDED'),0) AS mockPaymentRefunded
                    FROM payment p
                    JOIN user_order o ON o.id=p.order_id
                    WHERE o.store_id=? AND p.channel='MOCK'
                      AND p.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    """, storeId, days);
            Map<String, Object> evaluations = jdbc.queryForMap("""
                    SELECT COUNT(*) AS evaluationCases,
                           COALESCE(SUM(passed),0) AS evaluationPassed
                    FROM agent_eval_result e
                    JOIN agent_run r ON r.run_id=e.run_id
                    WHERE r.store_id=? AND e.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                    """, storeId, days);

            long runCount = number(runs.get("runCount"));
            long orderAttempts = number(runs.get("orderAttempts"));
            long knowledgeCalls = number(tools.get("knowledgeCalls"));
            long mockPayments = number(payments.get("mockPaymentOrders"));
            long evaluationCases = number(evaluations.get("evaluationCases"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", true);
            result.put("storeId", storeId);
            result.put("windowDays", days);
            result.put("runCount", runCount);
            result.put("succeededRuns", number(runs.get("succeededRuns")));
            result.put("failedRuns", number(runs.get("failedRuns")));
            result.put("successRate", ratio(number(runs.get("succeededRuns")), runCount));
            result.put("fallbackRuns", number(runs.get("fallbackRuns")));
            result.put("fallbackRate", ratio(number(runs.get("fallbackRuns")), runCount));
            result.put("averageLatencyMs", decimal(runs.get("averageLatencyMs")));
            result.put("inputTokens", number(runs.get("inputTokens")));
            result.put("outputTokens", number(runs.get("outputTokens")));
            result.put("totalTokens", number(runs.get("totalTokens")));
            result.put("orderAttempts", orderAttempts);
            result.put("successfulOrders", number(runs.get("successfulOrders")));
            result.put("planAdoptionRate", ratio(orderAttempts, runCount));
            result.put("orderConversionRate", ratio(number(runs.get("successfulOrders")), orderAttempts));
            result.put("totalModelCost", decimal(runs.get("totalModelCost")));
            result.put("costPerSuccessfulOrder", costPerOrder(runs.get("totalModelCost"), number(runs.get("successfulOrders"))));
            result.put("toolCalls", number(tools.get("toolCalls")));
            result.put("knowledgeCalls", knowledgeCalls);
            result.put("knowledgeSourceCoverage", ratio(number(tools.get("sourcedKnowledgeCalls")), knowledgeCalls));
            result.put("mockPaymentOrders", mockPayments);
            result.put("mockPaymentPaid", number(payments.get("mockPaymentPaid")));
            result.put("mockPaymentRefunded", number(payments.get("mockPaymentRefunded")));
            result.put("mockPaymentCompletionRate", ratio(number(payments.get("mockPaymentPaid")), mockPayments));
            result.put("mockPaymentRefundRate", ratio(number(payments.get("mockPaymentRefunded")), mockPayments));
            result.put("evaluationCases", evaluationCases);
            result.put("evaluationPassed", number(evaluations.get("evaluationPassed")));
            result.put("evaluationPassRate", ratio(number(evaluations.get("evaluationPassed")), evaluationCases));
            return result;
        } catch (DataAccessException ex) {
            throw new ServiceException(503, "AI 观测数据尚未初始化，请先执行 P2 数据库迁移");
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0D : Math.round((numerator * 10000D / denominator)) / 100D;
    }

    private double costPerOrder(Object totalCost, long successfulOrders) {
        if (successfulOrders <= 0) return 0D;
        return Math.round(decimal(totalCost) * 10000D / successfulOrders) / 10000D;
    }
}
