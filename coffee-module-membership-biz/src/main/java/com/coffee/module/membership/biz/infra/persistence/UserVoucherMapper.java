package com.coffee.module.membership.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户卡券 Mapper
 */
@Mapper
public interface UserVoucherMapper extends BaseMapper<UserVoucherPO> {
}
