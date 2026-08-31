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
            + "AND o.status = 'READY_FOR_DELIVERY' ORDER BY d.created_at ASC, d.id ASC")
    List<DeliveryOrderPO> selectAvailable();

    @Select("SELECT d.* FROM delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "WHERE d.rider_id = #{riderId} AND o.status <> 'CANCELED' "
            + "ORDER BY CASE d.status WHEN 'CLAIMED' THEN 1 WHEN 'PICKED_UP' THEN 2 "
            + "WHEN 'DELIVERING' THEN 3 WHEN 'DELIVERED' THEN 4 WHEN 'CANCELED' THEN 6 ELSE 5 END, "
            + "d.updated_at DESC, d.id DESC")
    List<DeliveryOrderPO> selectByRiderId(@Param("riderId") Long riderId);

    @Select("SELECT d.* FROM delivery_order d WHERE d.user_id = #{userId} ORDER BY d.created_at DESC, d.id DESC")
    List<DeliveryOrderPO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM delivery_order WHERE id = #{id}")
    DeliveryOrderPO selectById(@Param("id") Long id);

    @Select("SELECT * FROM delivery_order WHERE order_id = #{orderId}")
    DeliveryOrderPO selectByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'OPEN', d.updated_at = NOW() "
            + "WHERE d.order_id = #{orderId} AND d.status = 'WAITING_MERCHANT' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'READY_FOR_DELIVERY'")
    int openIfMerchantReady(@Param("orderId") Long orderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'CLAIMED', d.rider_id = #{riderId}, d.rider_name = #{riderName}, "
            + "d.claimed_at = NOW(), d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.status = 'OPEN' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'READY_FOR_DELIVERY'")
    int claimIfOpen(@Param("deliveryOrderId") Long deliveryOrderId,
                    @Param("riderId") Long riderId,
                    @Param("riderName") String riderName);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'PICKED_UP', d.picked_up_at = NOW(), d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.rider_id = #{riderId} AND d.status = 'CLAIMED' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'RIDER_ASSIGNED'")
    int markPickedUp(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'DELIVERING', d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.rider_id = #{riderId} AND d.status = 'PICKED_UP' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'RIDER_ASSIGNED'")
    int markDelivering(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'DELIVERED', d.delivered_at = NOW(), d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.rider_id = #{riderId} AND d.status = 'DELIVERING' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'DELIVERING'")
    int markDelivered(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order d JOIN user_order o ON o.id = d.order_id "
            + "SET d.status = 'OPEN', d.rider_id = NULL, d.rider_name = NULL, "
            + "d.claimed_at = NULL, d.updated_at = NOW() "
            + "WHERE d.id = #{deliveryOrderId} AND d.rider_id = #{riderId} AND d.status = 'CLAIMED' "
            + "AND o.fulfillment_type = 'DELIVERY' AND o.status = 'RIDER_ASSIGNED'")
    int releaseClaim(@Param("deliveryOrderId") Long deliveryOrderId, @Param("riderId") Long riderId);

    @Update("UPDATE delivery_order SET status = 'CANCELED', updated_at = NOW() "
            + "WHERE order_id = #{orderId} AND status IN ('WAITING_MERCHANT', 'OPEN')")
    int cancelPending(@Param("orderId") Long orderId);
}
