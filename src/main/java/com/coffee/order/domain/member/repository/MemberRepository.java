package com.coffee.order.domain.member.repository;

import com.coffee.order.domain.member.entity.Member;

import java.util.Optional;

/**
 * 会员仓储接口
 */
public interface MemberRepository {
    Optional<Member> findById(Long id);
    Optional<Member> findByOpenid(String openid);
    Member save(Member member);
    void updateTotalSpent(Long id, double newTotalSpent);
}
