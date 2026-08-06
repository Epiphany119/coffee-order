package com.coffee.module.membership.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员卡持久化对象（member_card 表）
 */
@Data
@TableName("member_card")
public class MemberCardPO {
    @TableId(type = IdType.AUTO)
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
}
