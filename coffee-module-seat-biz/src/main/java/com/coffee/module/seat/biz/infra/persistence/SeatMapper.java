package com.coffee.module.seat.biz.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 座位 Mapper
 */
public interface SeatMapper extends BaseMapper<SeatPO> {

    /** 容量足够且空闲的座位，按容量升序 */
    @Select("SELECT * FROM seat WHERE status = 'FREE' AND capacity >= #{capacity} " +
            "ORDER BY capacity ASC, id ASC LIMIT #{limit}")
    List<SeatPO> selectFreeSeats(@Param("capacity") int capacity, @Param("limit") int limit);

    /** 乐观分配：仅当仍空闲时更新 */
    @Update("UPDATE seat SET status = 'ASSIGNED', assigned_user_id = #{userId}, " +
            "assigned_guest_id = #{guestId}, assigned_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'FREE'")
    int updateAssign(@Param("id") Long id, @Param("userId") Long userId, @Param("guestId") String guestId);

    /** 落座：座位编号即凭证，任何状态（空闲/已分配/已被他人占用）扫码均可直接落座，
     *  并记录占用者（assigned_user_id/assigned_guest_id），用于前端归属校验 */
    @Update("UPDATE seat SET status = 'OCCUPIED', assigned_user_id = #{userId}, " +
            "assigned_guest_id = #{guestId}, occupied_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateOccupy(@Param("id") Long id, @Param("userId") Long userId, @Param("guestId") String guestId);

    /** 离座：仅当已落座时释放 */
    @Update("UPDATE seat SET status = 'FREE', assigned_user_id = NULL, assigned_guest_id = NULL, " +
            "assigned_at = NULL, occupied_at = NULL, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'OCCUPIED'")
    int updateLeave(@Param("id") Long id);

    /** 超时释放：分配超过时限未落座 */
    @Update("UPDATE seat SET status = 'FREE', assigned_user_id = NULL, assigned_guest_id = NULL, " +
            "assigned_at = NULL, updated_at = NOW() " +
            "WHERE status = 'ASSIGNED' AND assigned_at < #{before}")
    int updateReleaseExpired(@Param("before") LocalDateTime before);

    /** 按 店名+编号 查询 */
    @Select("SELECT * FROM seat WHERE store_name = #{storeName} AND seat_no = #{seatNo} LIMIT 1")
    SeatPO selectByCode(@Param("storeName") String storeName, @Param("seatNo") String seatNo);
}
