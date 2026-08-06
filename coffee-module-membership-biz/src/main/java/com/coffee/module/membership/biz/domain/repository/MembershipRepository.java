package com.coffee.module.membership.biz.domain.repository;

import com.coffee.module.membership.biz.domain.MemberCard;

/**
 * 会员卡仓储接口
 */
public interface MembershipRepository {

    /** 按用户查询会员卡 */
    MemberCard findByUserId(Long userId);

    /** 按 ID 查询会员卡 */
    MemberCard findById(Long id);

    /** 保存（新增或更新） */
    MemberCard save(MemberCard card);
}
