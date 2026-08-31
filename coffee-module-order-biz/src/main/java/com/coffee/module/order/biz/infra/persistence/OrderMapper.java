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

    /** 商家/管理端全量列表：不含待支付（UNPAID）订单 */
    @Select("SELECT * FROM user_order WHERE status != 'UNPAID' ORDER BY created_at DESC")
    java.util.List<OrderPO> selectAll();

    /** 商家按店列表：不含待支付（UNPAID）订单 */
    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} AND status != 'UNPAID' ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByStoreId(@Param("storeId") Long storeId);

    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} AND status = #{status} AND status != 'UNPAID' ORDER BY created_at DESC")
    java.util.List<OrderPO> selectByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") String status);

    @Select("SELECT * FROM user_order WHERE store_id = #{storeId} AND status != 'UNPAID' ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<OrderPO> selectRecentByStoreId(@Param("storeId") Long storeId, @Param("limit") int limit);

    /** 今日营业额与订单数（营业额计入 COMPLETED/DELIVERED；订单数不含待支付 UNPAID） */
    @Select("SELECT COALESCE(SUM(CASE WHEN status IN ('COMPLETED', 'DELIVERED') THEN final_price ELSE 0 END), 0) AS revenue, " +
            "COUNT(CASE WHEN status != 'UNPAID' THEN 1 END) AS cnt FROM user_order WHERE store_id = #{storeId} AND DATE(created_at) = CURDATE()")
    java.util.Map<String, Object> selectTodayStats(@Param("storeId") Long storeId);

    /** 待处理订单数 */
    @Select("SELECT COUNT(*) FROM user_order WHERE store_id = #{storeId} " +
            "AND status IN ('PENDING', 'ACCEPTED', 'PREPARING')")
    long countPendingByStoreId(@Param("storeId") Long storeId);

    /** 近 N 天每日营业额（计入 COMPLETED/DELIVERED；day=YYYYMMDD；ROUND 消除 DOUBLE 浮点噪声） */
    @Select("SELECT DATE_FORMAT(created_at, '%Y%m%d') AS day, " +
            "ROUND(COALESCE(SUM(CASE WHEN status IN ('COMPLETED', 'DELIVERED') THEN final_price ELSE 0 END), 0), 2) AS amount " +
            "FROM user_order WHERE store_id = #{storeId} " +
            "AND created_at >= DATE_SUB(CURDATE(), INTERVAL (#{days} - 1) DAY) " +
            "GROUP BY DATE_FORMAT(created_at, '%Y%m%d') ORDER BY MIN(created_at)")
    java.util.List<java.util.Map<String, Object>> selectDailySales(@Param("storeId") Long storeId, @Param("days") int days);

    /** 近 N 周每周营业额（周一起始；day=周起日期 YYYYMMDD；计入 COMPLETED/DELIVERED；ROUND 消除浮点噪声） */
    @Select("SELECT DATE_FORMAT(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), '%Y%m%d') AS day, " +
            "ROUND(COALESCE(SUM(CASE WHEN status IN ('COMPLETED', 'DELIVERED') THEN final_price ELSE 0 END), 0), 2) AS amount " +
            "FROM user_order WHERE store_id = #{storeId} " +
            "AND created_at >= DATE_SUB(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL (#{weeks} - 1) * 7 DAY) " +
            "AND created_at < DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY) " +
            "GROUP BY DATE_FORMAT(DATE_SUB(created_at, INTERVAL WEEKDAY(created_at) DAY), '%Y%m%d') ORDER BY MIN(created_at)")
    java.util.List<java.util.Map<String, Object>> selectWeeklySales(@Param("storeId") Long storeId, @Param("weeks") int weeks);

    @Update("UPDATE user_order SET status = #{status} WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 状态机 CAS：只有数据库中的旧状态仍匹配时才允许流转。 */
    @Update("UPDATE user_order SET status = #{status} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusIfCurrent(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                              @Param("status") String status);

    /** 店铺当日最大订单顺序号：order_no 末段数字的最大值，无则 0 */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(order_no, '-', -1) AS UNSIGNED)), 0) " +
            "FROM user_order WHERE store_id = #{storeId} AND order_no LIKE CONCAT(#{prefix}, '-%')")
    int selectMaxSeq(@Param("storeId") Long storeId, @Param("prefix") String prefix);
}
