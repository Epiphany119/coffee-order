package com.coffee.module.seat.biz.domain.repository;

import com.coffee.module.seat.biz.domain.Seat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 座位仓储接口
 */
public interface SeatRepository {

    /** 查询容量足够且空闲的座位（按容量升序，取前 N 张） */
    List<Seat> findFreeSeats(int requiredCapacity, int limit);

    /** 乐观分配：仅当座位仍为空闲时更新为已分配，返回是否成功 */
    boolean tryAssign(Long seatId, Long userId, String guestId);

    /** 落座：座位编号即凭证，任何状态均可落座，并记录占用者，返回是否成功 */
    boolean occupy(Long seatId, Long userId, String guestId);

    /** 离座：仅当状态为已落座时释放，返回是否成功 */
    boolean leave(Long seatId);

    /** 释放所有分配时间早于指定时间的已分配座位（超时释放），返回释放数量 */
    int releaseExpired(LocalDateTime before);

    Seat findById(Long seatId);

    /** 按 店名+编号 查询 */
    Seat findByCode(String storeName, String seatNo);

    List<Seat> findAll();

    long count();

    void saveAll(List<Seat> seats);
}
