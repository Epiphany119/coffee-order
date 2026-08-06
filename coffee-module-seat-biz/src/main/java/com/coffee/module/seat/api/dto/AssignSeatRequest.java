package com.coffee.module.seat.api.dto;

/**
 * 分配座位请求
 */
public class AssignSeatRequest {
    /** 店铺 id（座位按店隔离，用户端当前店铺） */
    private Long storeId;
    /** 就餐人数（1-8） */
    private int peopleCount;
    /** 登录用户 id（可选，游客为空） */
    private Long userId;
    /** 游客标识（可选） */
    private String guestId;

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public int getPeopleCount() { return peopleCount; }
    public void setPeopleCount(int peopleCount) { this.peopleCount = peopleCount; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
}
