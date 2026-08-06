package com.coffee.module.membership.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员卡 Mapper
 */
@Mapper
public interface MemberCardMapper extends BaseMapper<MemberCardPO> {
}
