package com.coffee.module.membership.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 等级规则 DTO
 */
@Data
public class LevelRuleDTO {
    /** 等级列表（含阈值/折扣） */
    private List<LevelItem> levels;
    /** 积分规则说明（如 消费1元=1积分） */
    private String pointsRule;

    @Data
    public static class LevelItem {
        private String level;
        private String label;
        /** 升级所需累计消费（0 表示初始等级） */
        private double threshold;
        /** 折扣率 */
        private double discountRate;
    }
}
