package com.coffee.module.order.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

/**
 * 订单 Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {

    @Select("SELECT * FROM user_order WHERE user_id = #{userId} ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_order WHERE guest_id = #{guestId} ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByGuestId(@Param("guestId") String guestId);

    @Select("SELECT * FROM user_order ORDER BY created_at DESC")
    java.util.List<OrderPO> selectAll();

    @Update("UPDATE user_order SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
