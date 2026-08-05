package com.coffee.module.member.biz.domain.service;

import com.coffee.module.member.biz.domain.Member;
import com.coffee.module.member.biz.domain.repository.MemberRepository;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberDTO;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

/**
 * 会员领域服务
 */
@Service
public class MemberDomainService implements MemberService {

    private final MemberRepository memberRepository;

    public MemberDomainService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public MemberDTO getMember(Long id) {
        Member member = memberRepository.findById(id);
        if (member == null) {
            throw new ServiceException(404, "会员不存在");
        }
        return toDTO(member);
    }

    @Override
    public MemberLevelDTO getMemberLevel(Long userId) {
        if (userId == null || userId <= 0) {
            return MemberLevelDTO.fromTotalSpent(0);
        }
        Member member = memberRepository.findById(userId);
        if (member == null) {
            return MemberLevelDTO.fromTotalSpent(0);
        }
        return MemberLevelDTO.fromTotalSpent(member.getTotalSpent() != null ? member.getTotalSpent() : 0);
    }

    @Override
    public void addSpending(Long userId, double amount) {
        if (userId == null || userId <= 0 || amount <= 0) return;
        Member member = memberRepository.findById(userId);
        if (member != null) {
            member.addSpending(amount);
            memberRepository.save(member);
        }
    }

    @Override
    public double getTotalSpent(Long userId) {
        if (userId == null || userId <= 0) return 0;
        Member member = memberRepository.findById(userId);
        return member != null && member.getTotalSpent() != null ? member.getTotalSpent() : 0;
    }

    private MemberDTO toDTO(Member member) {
        MemberDTO dto = new MemberDTO();
        dto.setId(member.getId());
        dto.setOpenid(member.getOpenid());
        dto.setNickname(member.getNickname());
        dto.setAvatarUrl(member.getAvatarUrl());
        dto.setTotalSpent(member.getTotalSpent());
        dto.setMemberLevel(member.getLevel());
        dto.setPoints(member.getPoints());
        dto.setPointsLevel(member.getPointsLevel());
        return dto;
    }
}
