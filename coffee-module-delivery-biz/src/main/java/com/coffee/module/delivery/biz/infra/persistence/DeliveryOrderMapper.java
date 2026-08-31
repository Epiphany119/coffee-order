package com.coffee.module.delivery.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrderPO> {

    @Select("SELECT d.* FROM delivery_order d "
            + "JOIN user_order o ON o.id = d.order_id "
            + "WHERE d.status = 'OPEN' AND o.fulfillment_type = 'DELIVERY' "
            + "AND o.status NOT IN ('UNPAID', 'CANCELED') ORDER BY d.created_at ASC, d.id ASC")
    List<DeliveryOrderPO> selectAvailable();

    @Select("SELECT d.* FROM delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "WHERE d.rider_id = #{riderId} AND o.status <> 'CANCELED' "
            + "ORDER BY CASE d.status WHEN 'CLAIMED' THEN 1 WHEN 'PICKED_UP' THEN 2 "
            + "WHEN 'DELIVERING' THEN 3 WHEN 'DELIVERED' THEN 4 ELSE 5 END, d.updated_at DESC, d.id DESC")
    List<DeliveryOrderPO> selectByRiderId(@Param("riderId") Long riderId);

    @Select("SELECT d.* FROM delivery_order d WHERE d.user_id = #{userId} ORDER BY d.created_at DESC, d.id DESC")
    List<DeliveryOrderPO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM delivery_order WHERE id = #{id}")
    DeliveryOrderPO selectById(@Param("id") Long id);

    @Select("SELECT * FROM delivery_order WHERE order_id = #{orderId}")
    DeliveryOrderPO selectByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'CLAIMED', d.rider_id = #{riderId}, d.rider_name = #{riderName}, "
            + "d.claimed_at = NOW(), d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.status = 'OPEN' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status NOT IN ('UNPAID', 'CANCELED')")
    int claimIfOpen(@Param("deliveryOrderId") Long deliveryOrderId,
                    @Param("riderId") Long riderId,
                    @Param("riderName") String riderName);

    @Update("UPDATE delivery_order SET status = 'PICKED_UP', picked_up_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{deliveryOrderId} AND rider_id = #{riderId} AND status = 'CLAIMED'")
    int markPickedUp(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order SET status = 'DELIVERING', updated_at = NOW() "
            + "WHERE id = #{deliveryOrderId} AND rider_id = #{riderId} AND status = 'PICKED_UP'")
    int markDelivering(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order SET status = 'DELIVERED', delivered_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{deliveryOrderId} AND rider_id = #{riderId} AND status = 'DELIVERING'")
    int markDelivered(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order SET status = 'OPEN', rider_id = NULL, rider_name = NULL, "
            + "claimed_at = NULL, updated_at = NOW() "
            + "WHERE id = #{deliveryOrderId} AND rider_id = #{riderId} AND status = 'CLAIMED'")
    int releaseClaim(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);
}
