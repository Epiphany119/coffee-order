package com.coffee.module.customeragent.api.dto;

/** Agent 已校验过的下单行；确认阶段只允许服务端使用该快照。 */
public record AgentOrderLine(String productCode, String size, int quantity) { }
