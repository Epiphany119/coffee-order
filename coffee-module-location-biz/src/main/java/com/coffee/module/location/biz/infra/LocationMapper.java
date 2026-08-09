package com.coffee.module.location.biz.infra;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Mapper;

/** 定位持久化 Mapper；模块使用 infra 包，显式标注以纳入 MyBatis 扫描。 */
@Mapper
public interface LocationMapper extends BaseMapper<LocationPO> {
    @Select("SELECT * FROM user_location WHERE user_id=#{userId} LIMIT 1") LocationPO findByUserId(@Param("userId") Long userId);
}
