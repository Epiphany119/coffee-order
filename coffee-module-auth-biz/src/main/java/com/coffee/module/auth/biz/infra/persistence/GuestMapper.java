package com.coffee.module.auth.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游客会话 Mapper
 */
@Mapper
public interface GuestMapper extends BaseMapper<GuestPO> {
}
