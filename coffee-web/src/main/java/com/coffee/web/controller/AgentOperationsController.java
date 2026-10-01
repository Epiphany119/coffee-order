package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.web.agent.AgentEvaluationCase;
import com.coffee.web.agent.AgentEvaluationService;
import com.coffee.web.agent.AgentRunAuditService;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-operations")
public class AgentOperationsController {
    private final AgentEvaluationService evaluationService;
    private final AgentRunAuditService audit;

    public AgentOperationsController(AgentEvaluationService evaluationService, AgentRunAuditService audit) {
        this.evaluationService = evaluationService;
        this.audit = audit;
    }

    @GetMapping("/runs/{runId}")
    public Result<Map<String, Object>> readRun(@PathVariable String runId) {
        return Result.success(audit.read(AccessGuard.currentIdentity(), runId));
    }

    @GetMapping("/evaluations/cases")
    public Result<List<AgentEvaluationCase>> evaluationCases() {
        AccessGuard.currentIdentity();
        return Result.success(evaluationService.cases());
    }

    @PostMapping("/evaluations/run")
    public Result<AgentEvaluationService.EvaluationResult> runEvaluation(@RequestBody EvaluationRequest request) {
        if (request == null || request.caseId == null || request.caseId.isBlank()) {
            throw new ServiceException(400, "Evaluation case is required");
        }
        return Result.success(evaluationService.run(AccessGuard.currentIdentity(),
                request.caseId.trim(), request.storeId, request.sessionId));
    }

    @PostMapping("/evaluations/run-all")
    public Result<List<AgentEvaluationService.EvaluationResult>> runAllEvaluations(
            @RequestBody(required = false) EvaluationRequest request) {
        return Result.success(evaluationService.runAll(AccessGuard.currentIdentity(),
                request == null ? null : request.storeId));
    }

    @GetMapping("/evaluations/summary")
    public Result<Map<String, Object>> evaluationSummary() {
        return Result.success(evaluationService.summary(AccessGuard.currentIdentity()));
    }

    public static class EvaluationRequest {
        public String caseId;
        public Long storeId;
        public String sessionId;
    }
}
