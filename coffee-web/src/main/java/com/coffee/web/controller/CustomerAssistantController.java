package com.coffee.web.controller;

import com.coffee.common.core.result.Result;
import com.coffee.web.agent.CustomerSupervisorAgentOrchestrator;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 顾客侧统一 Agent 入口：Supervisor 负责把请求分发给受控子 Agent。 */
@RestController
@RequestMapping("/api/customer-agent")
public class CustomerAssistantController {
    private final CustomerSupervisorAgentOrchestrator supervisor;

    public CustomerAssistantController(CustomerSupervisorAgentOrchestrator supervisor) {
        this.supervisor = supervisor;
    }

    @PostMapping("/assistant")
    public Result<CustomerSupervisorAgentOrchestrator.AssistantAnswer> ask(@RequestBody AssistantRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        RequestIdentity identity = AccessGuard.currentIdentity();
        return Result.success(supervisor.execute(identity, request.storeId, request.sessionId, request.message));
    }

    public static class AssistantRequest {
        public Long storeId;
        public String sessionId;
        public String message;
    }
}
