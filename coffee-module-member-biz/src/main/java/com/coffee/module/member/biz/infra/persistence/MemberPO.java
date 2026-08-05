package com.coffee.module.member.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会员持久化对象
 */
@Data
@TableName("coffee_user")
public class MemberPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Double totalSpent;
    private String memberLevel;
    private Integer points;
    private String pointsLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
