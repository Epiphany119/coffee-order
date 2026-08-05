package com.coffee.order.infrastructure.persistence.repository;

import com.coffee.order.domain.member.entity.Member;
import com.coffee.order.domain.member.repository.MemberRepository;
import com.coffee.order.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA 会员仓储实现
 */
@Repository
public class JpaMemberRepository implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    public JpaMemberRepository(MemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Member> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByOpenid(String openid) {
        return jpaRepository.findByOpenid(openid).map(this::toDomain);
    }

    @Override
    public Member save(Member member) {
        MemberEntity entity = toEntity(member);
        entity = jpaRepository.save(entity);
        return toDomain(entity);
    }

    @Override
    public void updateTotalSpent(Long id, double newTotalSpent) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setTotalSpent(newTotalSpent);
            jpaRepository.save(entity);
        });
    }

    private Member toDomain(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        member.setOpenid(entity.getOpenid());
        member.setNickname(entity.getNickname());
        member.setAvatarUrl(entity.getAvatarUrl());
        member.setTotalSpent(entity.getTotalSpent());
        member.setMemberLevel(entity.getMemberLevel());
        member.setCreatedAt(entity.getCreatedAt());
        member.setUpdatedAt(entity.getUpdatedAt());
        return member;
    }

    private MemberEntity toEntity(Member member) {
        MemberEntity entity = new MemberEntity();
        entity.setId(member.getId());
        entity.setOpenid(member.getOpenid());
        entity.setNickname(member.getNickname());
        entity.setAvatarUrl(member.getAvatarUrl());
        entity.setTotalSpent(member.getTotalSpent());
        entity.setMemberLevel(member.getMemberLevel());
        return entity;
    }

    public interface MemberJpaRepository extends JpaRepository<MemberEntity, Long> {
        Optional<MemberEntity> findByOpenid(String openid);
    }
}
