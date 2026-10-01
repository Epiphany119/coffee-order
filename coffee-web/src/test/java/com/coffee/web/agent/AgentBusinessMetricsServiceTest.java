package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class AgentBusinessMetricsServiceTest {

    @Test
    void clampsWindowAndCalculatesOperationalRatios() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(
                Map.ofEntries(Map.entry("runCount", 4L), Map.entry("succeededRuns", 3L),
                        Map.entry("failedRuns", 1L), Map.entry("fallbackRuns", 1L),
                        Map.entry("orderAttempts", 2L), Map.entry("successfulOrders", 1L),
                        Map.entry("inputTokens", 100L), Map.entry("outputTokens", 50L),
                        Map.entry("totalTokens", 150L), Map.entry("totalModelCost", 0.50D),
                        Map.entry("averageLatencyMs", 42.5D)),
                Map.of("toolCalls", 5L, "knowledgeCalls", 4L, "sourcedKnowledgeCalls", 3L),
                Map.of("mockPaymentOrders", 2L, "mockPaymentPaid", 1L, "mockPaymentRefunded", 1L),
                Map.of("evaluationCases", 4L, "evaluationPassed", 3L));
        AgentBusinessMetricsService service = new AgentBusinessMetricsService(jdbc);

        Map<String, Object> result = service.summarize(
                new RequestIdentity(RequestIdentity.Kind.MERCHANT, 7L, null), 11L, 120);

        assertEquals(90, result.get("windowDays"));
        assertEquals(4L, result.get("runCount"));
        assertEquals(75D, result.get("successRate"));
        assertEquals(25D, result.get("fallbackRate"));
        assertEquals(50D, result.get("planAdoptionRate"));
        assertEquals(50D, result.get("orderConversionRate"));
        assertEquals(0.5D, result.get("costPerSuccessfulOrder"));
        assertEquals(75D, result.get("knowledgeSourceCoverage"));
        assertEquals(50D, result.get("mockPaymentCompletionRate"));
        assertEquals(75D, result.get("evaluationPassRate"));
    }

    @Test
    void rejectsNonMerchantIdentity() {
        AgentBusinessMetricsService service = new AgentBusinessMetricsService(new StubJdbcTemplate());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.summarize(new RequestIdentity(RequestIdentity.Kind.USER, 7L, null), 11L, 7));

        assertEquals(403, exception.getCode());
    }

    @Test
    void reportsUnavailableSchemaAs503() {
        AgentBusinessMetricsService service = new AgentBusinessMetricsService(new FailingJdbcTemplate());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.summarize(new RequestIdentity(RequestIdentity.Kind.MERCHANT, 7L, null), 11L, 7));

        assertEquals(503, exception.getCode());
    }

    private static class StubJdbcTemplate extends JdbcTemplate {
        private final Queue<Map<String, Object>> results = new ArrayDeque<>();

        @SafeVarargs
        private StubJdbcTemplate(Map<String, Object>... results) {
            this.results.addAll(java.util.List.of(results));
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            return results.remove();
        }
    }

    private static final class FailingJdbcTemplate extends JdbcTemplate {
        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            throw new BadSqlGrammarException("metrics", sql, new SQLException("table missing"));
        }
    }
}
