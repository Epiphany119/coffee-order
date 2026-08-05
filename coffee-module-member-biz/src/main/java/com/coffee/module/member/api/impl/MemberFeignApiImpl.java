package com.coffee.module.member.api.impl;

import com.coffee.module.member.api.MemberFeignApi;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberDTO;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import org.springframework.stereotype.Service;

/**
 * 会员服务 Feign 实现
 */
@Service
public class MemberFeignApiImpl implements MemberFeignApi {

    private final MemberService memberService;

    public MemberFeignApiImpl(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public MemberDTO getMemberById(Long id) {
        return memberService.getMember(id);
    }

    @Override
    public MemberLevelDTO getMemberLevel(Long userId) {
        return memberService.getMemberLevel(userId);
    }

    @Override
    public void addSpending(Long userId, double amount) {
        memberService.addSpending(userId, amount);
    }

    @Override
    public double getTotalSpent(Long userId) {
        return memberService.getTotalSpent(userId);
    }
}
