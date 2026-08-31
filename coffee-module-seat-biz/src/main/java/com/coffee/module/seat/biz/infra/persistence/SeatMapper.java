package com.coffee.module.seat.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 座位 Mapper
 * seat 表只存 store_id + template_id 映射与运行时状态，
 * 查询统一 JOIN seat_template（座位规格）与 store（店名）补全展示字段
 */
public interface SeatMapper extends BaseMapper<SeatPO> {

    /** 展示字段公共前缀：店名/桌号/容量来自 JOIN 表 */
    String JOIN_COLUMNS = "s.*, st.name AS store_name, t.seat_no, t.capacity ";

    /** 容量足够且空闲的座位（按店隔离），按容量升序 */
    @Select("SELECT " + JOIN_COLUMNS + "FROM seat s " +
            "JOIN store st ON s.store_id = st.id " +
            "JOIN seat_template t ON s.template_id = t.id " +
            "WHERE s.store_id = #{storeId} AND s.status = 'FREE' " +
            "AND t.capacity >= #{capacity} ORDER BY t.capacity ASC, s.id ASC LIMIT #{limit}")
    List<SeatPO> selectFreeSeats(@Param("storeId") Long storeId,
                                 @Param("capacity") int capacity, @Param("limit") int limit);

    /** 店铺座位数量（初始化补座用） */
    @Select("SELECT COUNT(*) FROM seat WHERE store_id = #{storeId}")
    long countByStoreId(@Param("storeId") Long storeId);

    /** 乐观分配：仅当仍空闲时更新 */
    @Update("UPDATE seat SET status = 'ASSIGNED', assigned_user_id = #{userId}, " +
            "assigned_guest_id = #{guestId}, assigned_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'FREE'")
    int updateAssign(@Param("id") Long id, @Param("userId") Long userId, @Param("guestId") String guestId);

    /** 落座：空闲座位可直接落座；已分配座位仅允许原分配身份落座。 */
    @Update("UPDATE seat SET status = 'OCCUPIED', assigned_user_id = #{userId}, " +
            "assigned_guest_id = #{guestId}, occupied_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND (status = 'FREE' OR " +
            "(status = 'ASSIGNED' AND ((#{userId} IS NOT NULL AND assigned_user_id = #{userId}) " +
            "OR (#{guestId} IS NOT NULL AND assigned_guest_id = #{guestId}))))")
    int updateOccupy(@Param("id") Long id, @Param("userId") Long userId, @Param("guestId") String guestId);

    /** 离座：仅当已落座且当前身份是占用者时释放 */
    @Update("UPDATE seat SET status = 'FREE', assigned_user_id = NULL, assigned_guest_id = NULL, " +
            "assigned_at = NULL, occupied_at = NULL, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'OCCUPIED' AND ((#{userId} IS NOT NULL AND assigned_user_id = #{userId}) " +
            "OR (#{guestId} IS NOT NULL AND assigned_guest_id = #{guestId}))")
    int updateLeave(@Param("id") Long id, @Param("userId") Long userId, @Param("guestId") String guestId);

    /** 超时释放：分配超过时限未落座 */
    @Update("UPDATE seat SET status = 'FREE', assigned_user_id = NULL, assigned_guest_id = NULL, " +
            "assigned_at = NULL, updated_at = NOW() " +
            "WHERE status = 'ASSIGNED' AND assigned_at < #{before}")
    int updateReleaseExpired(@Param("before") LocalDateTime before);

    /** 按 店名+编号 查询（店名来自 store 表，桌号来自模板表） */
    @Select("SELECT " + JOIN_COLUMNS + "FROM seat s " +
            "JOIN store st ON s.store_id = st.id " +
            "JOIN seat_template t ON s.template_id = t.id " +
            "WHERE st.name = #{storeName} AND t.seat_no = #{seatNo} LIMIT 1")
    SeatPO selectByCode(@Param("storeName") String storeName, @Param("seatNo") String seatNo);

    /** 按 id 查询（JOIN 补全展示字段） */
    @Select("SELECT " + JOIN_COLUMNS + "FROM seat s " +
            "JOIN store st ON s.store_id = st.id " +
            "JOIN seat_template t ON s.template_id = t.id " +
            "WHERE s.id = #{id}")
    SeatPO selectByIdWithJoin(@Param("id") Long id);

    /** 店铺座位列表（storeId 为空查全部） */
    @Select("SELECT " + JOIN_COLUMNS + "FROM seat s " +
            "JOIN store st ON s.store_id = st.id " +
            "JOIN seat_template t ON s.template_id = t.id " +
            "WHERE (#{storeId} IS NULL OR s.store_id = #{storeId}) " +
            "ORDER BY s.store_id ASC, s.id ASC")
    List<SeatPO> selectListByStore(@Param("storeId") Long storeId);

    /** 指定店铺中指定身份（用户/游客）未释放的座位（已分配或已落座，幽灵占座恢复用），
     *  按占用/分配时间倒序。取号未落座（ASSIGNED）也必须找回，否则用户重新进入会重复取号占桌 */
    @Select("SELECT " + JOIN_COLUMNS + "FROM seat s " +
            "JOIN store st ON s.store_id = st.id " +
            "JOIN seat_template t ON s.template_id = t.id " +
            "WHERE s.store_id = #{storeId} AND s.status IN ('ASSIGNED', 'OCCUPIED') " +
            "AND ((#{userId} IS NOT NULL AND s.assigned_user_id = #{userId}) " +
            "     OR (#{guestId} IS NOT NULL AND s.assigned_guest_id = #{guestId})) " +
            "ORDER BY COALESCE(s.occupied_at, s.assigned_at) DESC")
    List<SeatPO> selectOccupiedByStoreAndIdentity(@Param("storeId") Long storeId,
                                                  @Param("userId") Long userId,
                                                  @Param("guestId") String guestId);
}
