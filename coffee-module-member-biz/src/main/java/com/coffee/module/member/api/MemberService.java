package com.coffee.module.member.api;

import com.coffee.module.member.api.dto.*;

/**
 * 会员服务 API
 */
public interface MemberService {

    /**
     * 获取会员信息
     */
    MemberDTO getMember(Long id);

    /**
     * 获取会员等级信息
     */
    MemberLevelDTO getMemberLevel(Long userId);

    /**
     * 增加消费金额
     */
    void addSpending(Long userId, double amount);

    /**
     * 获取累计消费
     */
    double getTotalSpent(Long userId);
}
