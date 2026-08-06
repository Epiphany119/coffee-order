package com.coffee.module.membership.biz.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员卡领域实体
 *
 * 等级/积分规则集中在 MembershipDomainService，本实体只负责状态变化
 */
@Data
public class MemberCard {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_FROZEN = 0;

    private Long id;
    private Long userId;
    private String cardNo;
    private String level;
    private Integer points;
    private Double totalSpent;
    private Integer exchangePoints;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 兑换：扣减积分并累计已兑换积分，返回是否成功（积分不足返回 false） */
    public boolean redeemPoints(int cost) {
        if (cost <= 0 || points == null || points < cost) return false;
        this.points -= cost;
        this.exchangePoints = (exchangePoints == null ? 0 : exchangePoints) + cost;
        return true;
    }
}
