package com.coffee.module.member.biz.infra.repository;

import com.coffee.module.member.biz.domain.Member;
import com.coffee.module.member.biz.domain.repository.MemberRepository;
import com.coffee.module.member.biz.infra.persistence.MemberMapper;
import com.coffee.module.member.biz.infra.persistence.MemberPO;
import org.springframework.stereotype.Repository;

/**
 * 会员仓储实现
 */
@Repository
public class MemberRepositoryImpl implements MemberRepository {

    private final MemberMapper memberMapper;

    public MemberRepositoryImpl(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Override
    public Member findById(Long id) {
        MemberPO po = memberMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public Member findByOpenid(String openid) {
        MemberPO po = memberMapper.selectByOpenid(openid);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public Member save(Member member) {
        MemberPO po = toPO(member);
        if (member.getId() == null) {
            memberMapper.insert(po);
            member.setId(po.getId());
        } else {
            memberMapper.updateById(po);
        }
        return member;
    }

    @Override
    public void updateTotalSpent(Long id, double totalSpent) {
        memberMapper.updateTotalSpent(id, totalSpent);
    }

    @Override
    public void updatePoints(Long id, int points) {
        memberMapper.updatePoints(id, points);
    }

    private Member toDomain(MemberPO po) {
        Member member = new Member();
        member.setId(po.getId());
        member.setOpenid(po.getOpenid());
        member.setNickname(po.getNickname());
        member.setAvatarUrl(po.getAvatarUrl());
        member.setTotalSpent(po.getTotalSpent());
        member.setMemberLevel(po.getMemberLevel());
        member.setPoints(po.getPoints());
        member.setPointsLevel(po.getPointsLevel());
        member.setCreatedAt(po.getCreatedAt());
        member.setUpdatedAt(po.getUpdatedAt());
        return member;
    }

    private MemberPO toPO(Member member) {
        MemberPO po = new MemberPO();
        po.setId(member.getId());
        po.setOpenid(member.getOpenid());
        po.setNickname(member.getNickname());
        po.setAvatarUrl(member.getAvatarUrl());
        po.setTotalSpent(member.getTotalSpent());
        po.setMemberLevel(member.getMemberLevel());
        po.setPoints(member.getPoints());
        po.setPointsLevel(member.getPointsLevel());
        return po;
    }
}
