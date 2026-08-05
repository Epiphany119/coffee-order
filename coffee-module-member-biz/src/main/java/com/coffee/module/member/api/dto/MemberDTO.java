package com.coffee.module.member.api.dto;

import lombok.Data;

/**
 * 会员信息 DTO
 */
@Data
public class MemberDTO {
    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Double totalSpent;
    private String memberLevel;
    private Integer points;
    private String pointsLevel;
}
