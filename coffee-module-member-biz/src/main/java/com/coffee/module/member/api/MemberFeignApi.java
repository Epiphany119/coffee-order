package com.coffee.module.member.api;

import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.module.member.api.dto.MemberDTO;

/**
 * 会员服务 Feign API
 */
public interface MemberFeignApi {

    MemberDTO getMemberById(Long id);

    MemberLevelDTO getMemberLevel(Long userId);

    void addSpending(Long userId, double amount);

    double getTotalSpent(Long userId);
}
