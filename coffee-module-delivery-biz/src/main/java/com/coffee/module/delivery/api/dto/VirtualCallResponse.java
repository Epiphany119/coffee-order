package com.coffee.module.delivery.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 虚拟电话中介响应。当前仅返回一次性会话框架，不返回真实电话号码。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCallResponse {
    /** NOT_CONFIGURED / READY / STARTED；当前实现为 NOT_CONFIGURED。 */
    private String status;
    /** 供后续第三方电话中介换取一次性虚拟号码或发起呼叫。 */
    private String relayId;
    private String provider;
    private boolean oneTime;
    private boolean dialable;
    private String message;
    private LocalDateTime expiresAt;

    public static VirtualCallResponse notConfigured(String relayId, LocalDateTime expiresAt) {
        return new VirtualCallResponse(
                "NOT_CONFIGURED",
                relayId,
                "VIRTUAL_NUMBER_RELAY",
                true,
                false,
                "虚拟电话中介服务尚未配置，已生成一次性转接会话框架",
                expiresAt);
    }
}
