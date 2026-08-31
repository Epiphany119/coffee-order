package com.coffee.module.seat.api;

import com.coffee.module.seat.api.dto.AssignSeatRequest;
import com.coffee.module.seat.api.dto.OccupySeatRequest;
import com.coffee.module.seat.api.dto.SeatResponse;

import java.util.List;

/**
 * 座位服务接口
 */
public interface SeatService {

    /**
     * 按人数分配空闲座位，返回座位信息与落座二维码
     */
    SeatResponse assignSeat(AssignSeatRequest request);

    /**
     * 解析二维码内容（座位编号），返回座位当前状态
     */
    SeatResponse resolveSeat(String code);

    /**
     * 确认落座（扫码后调用）
     */
    SeatResponse occupySeat(Long seatId, OccupySeatRequest request);

    /**
     * 离座释放
     */
    SeatResponse leaveSeat(Long seatId, Long userId, String guestId);

    /**
     * 店铺座位状态列表（管理用，storeId 为空查全部）
     */
    List<SeatResponse> listSeats(Long storeId);

    /**
     * 指定店铺中指定身份（用户/游客）已落座的座位（幽灵占座恢复用，按占用时间倒序）
     */
    List<SeatResponse> listOccupiedSeats(Long storeId, Long userId, String guestId);
}
