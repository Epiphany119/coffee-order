package com.coffee.order.domain.member.service;

import com.coffee.order.domain.member.entity.Member;
import com.coffee.order.domain.member.repository.MemberRepository;
import com.coffee.order.domain.member.valueobject.MemberLevel;
import org.springframework.stereotype.Service;

/**
 * 会员领域服务
 */
@Service
public class MemberDomainService {

    private final MemberRepository memberRepository;

    public MemberDomainService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public double getDiscountRate(Long userId) {
        if (userId == null || userId <= 0) return 1.0;
        return memberRepository.findById(userId)
                .map(user -> MemberLevel.fromTotalSpent(user.getTotalSpent()).discountRate())
                .orElse(1.0);
    }

    public MemberLevel getMemberLevel(Long userId) {
        if (userId == null || userId <= 0) return MemberLevel.REGULAR;
        return memberRepository.findById(userId)
                .map(user -> MemberLevel.fromTotalSpent(user.getTotalSpent()))
                .orElse(MemberLevel.REGULAR);
    }

    public void addSpending(Long userId, double amount) {
        if (userId == null || userId <= 0 || amount <= 0) return;
        memberRepository.findById(userId).ifPresent(member -> {
            member.addSpending(amount);
            member.setMemberLevel(MemberLevel.fromTotalSpent(member.getTotalSpent()).level());
            memberRepository.save(member);
        });
    }

    public double getTotalSpent(Long userId) {
        return memberRepository.findById(userId)
                .map(Member::getTotalSpent)
                .orElse(0.0);
    }
}
