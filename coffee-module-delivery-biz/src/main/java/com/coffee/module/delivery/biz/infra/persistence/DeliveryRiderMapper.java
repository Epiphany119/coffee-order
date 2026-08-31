package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliveryRiderMapper extends BaseMapper<DeliveryRiderPO> {

    @Select("SELECT * FROM delivery_rider WHERE username = #{username}")
    DeliveryRiderPO selectByUsername(@Param("username") String username);
}
