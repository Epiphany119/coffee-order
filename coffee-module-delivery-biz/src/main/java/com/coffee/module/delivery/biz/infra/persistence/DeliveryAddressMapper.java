package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeliveryAddressMapper extends BaseMapper<DeliveryAddressPO> {

    @Select("SELECT * FROM delivery_address WHERE user_id = #{userId} ORDER BY is_default DESC, id DESC")
    List<DeliveryAddressPO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM delivery_address WHERE id = #{addressId} AND user_id = #{userId}")
    DeliveryAddressPO selectOwned(@Param("userId") Long userId, @Param("addressId") Long addressId);

    @Select("SELECT COUNT(*) FROM delivery_address WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM delivery_address WHERE user_id = #{userId} ORDER BY id ASC LIMIT 1")
    DeliveryAddressPO selectFirstByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM delivery_address WHERE user_id = #{userId} AND id <> #{addressId} ORDER BY id ASC LIMIT 1")
    DeliveryAddressPO selectFirstOther(@Param("userId") Long userId, @Param("addressId") Long addressId);

    @Update("UPDATE delivery_address SET is_default = 0, updated_at = NOW() WHERE user_id = #{userId}")
    int clearDefault(@Param("userId") Long userId);

    @Update("UPDATE delivery_address SET is_default = 1, updated_at = NOW() WHERE id = #{addressId} AND user_id = #{userId}")
    int markDefault(@Param("userId") Long userId, @Param("addressId") Long addressId);

    @Delete("DELETE FROM delivery_address WHERE id = #{addressId} AND user_id = #{userId}")
    int deleteOwned(@Param("userId") Long userId, @Param("addressId") Long addressId);
}
