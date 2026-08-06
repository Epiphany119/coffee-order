package com.coffee.module.membership.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员卡 DTO
 */
@Data
public class MemberCardDTO {
    private Long id;
    private Long userId;
    /** 卡号 */
    private String cardNo;
    /** 等级 REGULAR / VIP / SVIP */
    private String level;
    /** 积分 */
    private Integer points;
    /** 累计消费 */
    private Double totalSpent;
    /** 累计已兑换积分 */
    private Integer exchangePoints;
    /** 状态：1 正常 / 0 冻结 */
    private Integer status;
    /** 等级折扣率（0.95 / 0.9 / 1.0） */
    private Double discountRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
