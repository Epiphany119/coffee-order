package com.coffee.module.order.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单明细 Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemPO> {

    @Select("SELECT * FROM order_item WHERE order_id = #{orderId} ORDER BY id")
    java.util.List<OrderItemPO> selectByOrderId(@Param("orderId") Long orderId);

    /** 运营大屏热销榜：只统计已完成订单，按售出件数再按销售额排序。 */
    @Select("SELECT i.product_name AS name, SUM(i.quantity) AS quantity, " +
            "ROUND(SUM(i.subtotal), 2) AS amount FROM order_item i " +
            "JOIN user_order o ON o.id = i.order_id " +
            "WHERE o.store_id = #{storeId} AND o.status = 'COMPLETED' " +
            "AND o.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY i.product_id, i.product_name ORDER BY quantity DESC, amount DESC LIMIT #{limit}")
    java.util.List<java.util.Map<String, Object>> selectHotProducts(@Param("storeId") Long storeId,
                                                                      @Param("days") int days,
                                                                      @Param("limit") int limit);
}
