package com.coffee.module.customeragent.api.dto;

import java.util.Locale;

/** Agent 已校验过的下单行；确认阶段只允许服务端使用该快照。 */
public record AgentOrderLine(String productCode, String size, int quantity,
                             Long productId, String productName) {
    public AgentOrderLine {
        productCode = productCode == null ? null : productCode.trim();
        size = size == null ? null : size.trim().toUpperCase(Locale.ROOT);
        productName = productName == null ? null : productName.trim();
    }

    /** 兼容非 Agent 调用方；进入 planToken 前仍必须补齐商品身份快照。 */
    public AgentOrderLine(String productCode, String size, int quantity) {
        this(productCode, size, quantity, null, null);
    }
}
