package com.coffee.module.seat.api.dto;

/**
 * 落座确认请求
 */
public class OccupySeatRequest {
    private Long userId;
    private String guestId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
}
