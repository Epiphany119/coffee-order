package com.coffee.module.membership.biz.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coffee.module.membership.biz.domain.MemberCard;
import com.coffee.module.membership.biz.domain.repository.MembershipRepository;
import com.coffee.module.membership.biz.infra.persistence.MemberCardMapper;
import com.coffee.module.membership.biz.infra.persistence.MemberCardPO;
import org.springframework.stereotype.Repository;

/**
 * 会员卡仓储实现
 */
@Repository
public class MembershipRepositoryImpl implements MembershipRepository {

    private final MemberCardMapper memberCardMapper;

    public MembershipRepositoryImpl(MemberCardMapper memberCardMapper) {
        this.memberCardMapper = memberCardMapper;
    }

    @Override
    public MemberCard findByUserId(Long userId) {
        MemberCardPO po = memberCardMapper.selectOne(new LambdaQueryWrapper<MemberCardPO>()
                .eq(MemberCardPO::getUserId, userId));
        return po != null ? toDomain(po) : null;
    }

    @Override
    public MemberCard findById(Long id) {
        MemberCardPO po = memberCardMapper.selectById(id);
        return po != null ? toDomain(po) : null;
    }

    @Override
    public MemberCard save(MemberCard card) {
        MemberCardPO po = toPO(card);
        if (card.getId() == null) {
            memberCardMapper.insert(po);
            card.setId(po.getId());
        } else {
            memberCardMapper.updateById(po);
        }
        return card;
    }

    private MemberCard toDomain(MemberCardPO po) {
        MemberCard card = new MemberCard();
        card.setId(po.getId());
        card.setUserId(po.getUserId());
        card.setCardNo(po.getCardNo());
        card.setLevel(po.getLevel());
        card.setPoints(po.getPoints());
        card.setTotalSpent(po.getTotalSpent());
        card.setExchangePoints(po.getExchangePoints());
        card.setStatus(po.getStatus());
        card.setCreatedAt(po.getCreatedAt());
        card.setUpdatedAt(po.getUpdatedAt());
        return card;
    }

    private MemberCardPO toPO(MemberCard card) {
        MemberCardPO po = new MemberCardPO();
        po.setId(card.getId());
        po.setUserId(card.getUserId());
        po.setCardNo(card.getCardNo());
        po.setLevel(card.getLevel());
        po.setPoints(card.getPoints());
        po.setTotalSpent(card.getTotalSpent());
        po.setExchangePoints(card.getExchangePoints());
        po.setStatus(card.getStatus());
        po.setCreatedAt(card.getCreatedAt());
        po.setUpdatedAt(card.getUpdatedAt());
        return po;
    }
}
