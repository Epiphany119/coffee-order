package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.web.agent.BusinessAgentOrchestrator;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 面向前端的统一 Agent API。SSE 仅推送本请求、当前身份的结果，避免跨用户串流。
 * 写操作不在本接口开放；顾客下单仍走 customer-agent 的 plan/confirm 两阶段接口。
 */
@RestController
@RequestMapping("/api/business-agent")
public class BusinessAgentController {
    private final BusinessAgentOrchestrator orchestrator;

    public BusinessAgentController(BusinessAgentOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping("/ask")
    public Result<BusinessAgentOrchestrator.AgentAnswer> ask(@RequestBody AskRequest request) {
        return Result.success(execute(request));
    }

    /** 仅允许当前身份读取自己的 Agent 运行轨迹，便于排查工具调用和面试演示。 */
    @GetMapping("/runs/{runId}")
    public Result<Map<String, Object>> readRun(@PathVariable String runId) {
        return Result.success(orchestrator.readRun(AccessGuard.currentIdentity(), runId));
    }

    @PostMapping("/knowledge/documents")
    public Result<Map<String, Object>> upsertKnowledge(@RequestBody KnowledgeRequest request) {
        if (request == null) throw new ServiceException(400, "缺少知识文档");
        orchestrator.upsertKnowledge(AccessGuard.currentIdentity(), request.storeId, request.title, request.content, request.source);
        return Result.success(Map.of("accepted", true, "message", "知识文档已保存，检索立即可用；向量索引将由异步任务增量同步"));
    }

    @PostMapping("/knowledge/bootstrap/menu")
    public Result<Map<String, Object>> bootstrapMenuKnowledge(@RequestBody KnowledgeBootstrapRequest request) {
        if (request == null || request.storeId == null) throw new ServiceException(400, "请选择要初始化的门店");
        orchestrator.bootstrapMenuKnowledge(AccessGuard.currentIdentity(), request.storeId);
        return Result.success(Map.of("accepted", true, "message", "菜单知识正在后台异步初始化，稍等片刻即可生效"));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AskRequest request) {
        RequestIdentity identity = AccessGuard.currentIdentity();
        validate(request, identity);
        SseEmitter emitter = new SseEmitter(45_000L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(Map.of("stage", "planning")));
                BusinessAgentOrchestrator.AgentAnswer result = orchestrator.execute(identity, request.scene, request.storeId, request.sessionId, request.message);
                emitter.send(SseEmitter.event().name("plan").data(Map.of(
                        "sessionId", result.sessionId(),
                        "runId", result.runId(),
                        "steps", result.plan(),
                        "structuredPlan", result.structuredPlan())));
                emitter.send(SseEmitter.event().name("tools").data(result.tools()));
                for (int index = 0; index < result.answer().length(); index += 8) {
                    emitter.send(SseEmitter.event().name("delta").data(result.answer().substring(index, Math.min(index + 8, result.answer().length()))));
                }
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", result.sessionId(), "runId", result.runId())));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message", "Agent 服务暂时不可用，请稍后重试"))); } catch (IOException ignored) { }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private BusinessAgentOrchestrator.AgentAnswer execute(AskRequest request) {
        RequestIdentity identity = AccessGuard.currentIdentity();
        validate(request, identity);
        return orchestrator.execute(identity, request.scene, request.storeId, request.sessionId, request.message);
    }

    private void validate(AskRequest request, RequestIdentity identity) {
        if (request == null || request.message == null || request.message.isBlank()) throw new ServiceException(400, "请输入想咨询的问题");
        if ("merchant".equalsIgnoreCase(request.scene) && identity.kind() != RequestIdentity.Kind.MERCHANT) {
            throw new ServiceException(403, "经营 Agent 仅支持商家身份");
        }
    }

    public static class AskRequest {
        public String scene = "customer";
        public String sessionId;
        public Long storeId;
        public String message;
    }
    public static class KnowledgeRequest {
        public Long storeId;
        public String title;
        public String content;
        public String source;
    }
    public static class KnowledgeBootstrapRequest { public Long storeId; }
}
