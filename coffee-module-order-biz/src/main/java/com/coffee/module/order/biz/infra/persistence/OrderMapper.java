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

    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByStoreId(@Param("storeId") Long storeId);

    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} AND status = #{status} ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") String status);

    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<OrderPO> selectRecentByStoreId(@Param("storeId") Long storeId, @Param("limit") int limit);

    /** 今日营业额与订单数 */
    @Select("SELECT COALESCE(SUM(final_price), 0) AS revenue, COUNT(*) AS cnt " +
            "FROM user_order WHERE store_id = #{storeId} AND DATE(created_at) = CURDATE()")
    java.util.Map<String, Object> selectTodayStats(@Param("storeId") Long storeId);

    /** 待处理订单数 */
    @Select("SELECT COUNT(*) FROM user_order WHERE store_id = #{storeId} AND status = 'PENDING'")
    long countPendingByStoreId(@Param("storeId") Long storeId);

    /** 近7天每日营业额 */
    @Select("SELECT DATE_FORMAT(created_at, '%m-%d') AS day, COALESCE(SUM(final_price), 0) AS amount " +
            "FROM user_order WHERE store_id = #{storeId} " +
            "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE_FORMAT(created_at, '%m-%d') ORDER BY MIN(created_at)")
    java.util.List<java.util.Map<String, Object>> selectWeekStats(@Param("storeId") Long storeId);

    @Update("UPDATE user_order SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
