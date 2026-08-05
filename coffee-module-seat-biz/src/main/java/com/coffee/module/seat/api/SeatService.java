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
    SeatResponse leaveSeat(Long seatId);

    /**
     * 全部座位状态（管理用）
     */
    List<SeatResponse> listSeats();
}
