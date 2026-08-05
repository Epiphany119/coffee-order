package com.coffee.module.seat.api.dto;

/**
 * 座位状态
 */
public enum SeatStatus {
    /** 空闲 */
    FREE,
    /** 已分配（等待落座） */
    ASSIGNED,
    /** 已落座 */
    OCCUPIED
}
