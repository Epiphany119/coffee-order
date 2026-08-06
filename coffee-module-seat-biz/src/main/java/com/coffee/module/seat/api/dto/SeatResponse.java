package com.coffee.module.seat.api.dto;

import java.time.LocalDateTime;

/**
 * 座位响应
 */
public class SeatResponse {
    private Long seatId;
    /** 店铺 id（前端据此校验座位归属店铺） */
    private Long storeId;
    private String storeName;
    private String seatNo;
    /** 座位编号，如 静安店-001 */
    private String code;
    private int capacity;
    private SeatStatus status;
    /** 占用者用户 ID（null=无） */
    private Long assignedUserId;
    /** 占用者游客 ID（null=无） */
    private String assignedGuestId;
    /** 分配时间（商家端展示） */
    private LocalDateTime assignedAt;
    /** 占用时间（商家端展示） */
    private LocalDateTime occupiedAt;
    /** 二维码内容（系统落座页 URL，仅分配接口返回） */
    private String qrContent;
    /** 二维码图片（PNG base64，data URL，仅分配接口返回） */
    private String qrBase64;

    public static SeatResponse from(SeatStatus status, Long seatId, Long storeId, String storeName, String seatNo,
                                    int capacity, Long assignedUserId, String assignedGuestId,
                                    LocalDateTime assignedAt, LocalDateTime occupiedAt,
                                    String qrContent, String qrBase64) {
        SeatResponse r = new SeatResponse();
        r.seatId = seatId;
        r.storeId = storeId;
        r.storeName = storeName;
        r.seatNo = seatNo;
        r.code = storeName + "-" + seatNo;
        r.capacity = capacity;
        r.status = status;
        r.assignedUserId = assignedUserId;
        r.assignedGuestId = assignedGuestId;
        r.assignedAt = assignedAt;
        r.occupiedAt = occupiedAt;
        r.qrContent = qrContent;
        r.qrBase64 = qrBase64;
        return r;
    }

    public Long getSeatId() { return seatId; }
    public Long getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
    public String getSeatNo() { return seatNo; }
    public String getCode() { return code; }
    public int getCapacity() { return capacity; }
    public SeatStatus getStatus() { return status; }
    public Long getAssignedUserId() { return assignedUserId; }
    public String getAssignedGuestId() { return assignedGuestId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getOccupiedAt() { return occupiedAt; }
    public String getQrContent() { return qrContent; }
    public String getQrBase64() { return qrBase64; }
}
