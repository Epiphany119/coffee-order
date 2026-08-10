package com.coffee.module.merchantagent.biz.domain;

import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** 对 Agent 与 HTTP 输入共同执行的动作白名单、范围与载荷校验。 */
@Component
public class GrowthActionPolicy {
    public ApprovedAction approve(String rawType, String rawTitle, Map<String, Object> proposal) {
        GrowthActionType type = GrowthActionType.parse(rawType);
        if (type == null) throw new ServiceException(400, "不支持的 Agent 操作类型");
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isBlank() || title.length() > 128) throw new ServiceException(400, "Agent 方案标题长度应为 1-128 个字符");
        if (proposal != null && proposal.values().stream().anyMatch(value -> value == null)) throw new ServiceException(400, "Agent 方案参数不能为 null");
        Map<String, Object> safeProposal = proposal == null ? Map.of() : Map.copyOf(proposal);
        Set<String> allowed = type == GrowthActionType.CREATE_VOUCHERS
                ? Set.of("discount", "minimum", "targetDays", "expiresDays", "message")
                : Set.of("targetDays", "message");
        if (!allowed.containsAll(safeProposal.keySet())) throw new ServiceException(400, "Agent 方案包含不允许的参数");
        Object message = safeProposal.get("message");
        if (message != null && String.valueOf(message).trim().length() > 300) throw new ServiceException(400, "Agent 触达文案不能超过 300 字");
        positiveInRange(safeProposal, "targetDays", 1, 90);
        if (type == GrowthActionType.CREATE_VOUCHERS) {
            positiveInRange(safeProposal, "discount", 1, 100);
            positiveInRange(safeProposal, "minimum", 1, 1000);
            positiveInRange(safeProposal, "expiresDays", 1, 30);
        }
        return new ApprovedAction(type, title, safeProposal);
    }
    private void positiveInRange(Map<String, Object> proposal, String key, int min, int max) {
        if (!proposal.containsKey(key)) return;
        try {
            int value = Integer.parseInt(String.valueOf(proposal.get(key)));
            if (value < min || value > max) throw new ServiceException(400, "Agent 参数 " + key + " 超出允许范围");
        } catch (NumberFormatException e) { throw new ServiceException(400, "Agent 参数 " + key + " 必须为整数"); }
    }
    public record ApprovedAction(GrowthActionType type, String title, Map<String, Object> proposal) { }
}
