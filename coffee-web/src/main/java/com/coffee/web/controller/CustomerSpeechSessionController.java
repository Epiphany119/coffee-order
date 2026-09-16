package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import com.coffee.web.speech.CustomerSpeechSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 受保护的实时语音识别连接票据入口。 */
@RestController
@RequestMapping("/api/customer-agent/transcription")
public class CustomerSpeechSessionController {
    private final CustomerSpeechSessionService sessions;
    private final String zhipuApiKey;

    public CustomerSpeechSessionController(CustomerSpeechSessionService sessions,
                                           @Value("${coffee.ai.zhipu.api-key:}") String zhipuApiKey) {
        this.sessions = sessions;
        this.zhipuApiKey = zhipuApiKey == null ? "" : zhipuApiKey.trim();
    }

    @PostMapping("/session")
    public Result<Map<String, Object>> issue(@RequestBody TranscriptionSessionRequest request) {
        RequestIdentity identity = AccessGuard.currentIdentity();
        if (identity.kind() != RequestIdentity.Kind.USER && identity.kind() != RequestIdentity.Kind.GUEST) {
            throw new ServiceException(403, "只有顾客身份可以使用语音点单");
        }
        if (request == null || request.storeId == null || request.storeId <= 0) {
            throw new ServiceException(400, "请选择门店后再使用语音点单");
        }
        if (zhipuApiKey.isBlank()) {
            throw new ServiceException(503, "流式语音识别服务尚未配置");
        }
        CustomerSpeechSessionService.IssuedSession issued = sessions.issue(identity, request.storeId);
        return Result.success(Map.of(
                "ticket", issued.ticket(),
                "expiresInSeconds", issued.expiresInSeconds()));
    }

    public static class TranscriptionSessionRequest {
        public Long storeId;
    }
}
