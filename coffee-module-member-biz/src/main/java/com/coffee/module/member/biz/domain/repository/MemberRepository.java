package com.coffee.module.member.biz.domain.repository;

import com.coffee.module.member.biz.domain.Member;

/**
 * 会员仓储接口
 */
public interface MemberRepository {

    Member findById(Long id);

    Member findByOpenid(String openid);

    Member save(Member member);

    void updateTotalSpent(Long id, double totalSpent);

    void updatePoints(Long id, int points);
}
