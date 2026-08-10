package com.coffee.module.merchantagent.biz.domain;

/** Agent 可提议的白名单动作。新增动作必须同时补齐审批、审计与执行实现。 */
public enum GrowthActionType {
    NOTIFY_MEMBERS, CREATE_VOUCHERS;

    public static GrowthActionType parse(String value) {
        try { return value == null ? null : valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
