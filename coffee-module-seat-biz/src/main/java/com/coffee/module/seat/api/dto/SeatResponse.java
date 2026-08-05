package com.coffee.module.seat.api.dto;

/**
 * 座位响应
 */
public class SeatResponse {
    private Long seatId;
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
    /** 二维码内容（系统落座页 URL，仅分配接口返回） */
    private String qrContent;
    /** 二维码图片（PNG base64，data URL，仅分配接口返回） */
    private String qrBase64;

    public static SeatResponse from(SeatStatus status, Long seatId, String storeName, String seatNo,
                                    int capacity, Long assignedUserId, String assignedGuestId,
                                    String qrContent, String qrBase64) {
        SeatResponse r = new SeatResponse();
        r.seatId = seatId;
        r.storeName = storeName;
        r.seatNo = seatNo;
        r.code = storeName + "-" + seatNo;
        r.capacity = capacity;
        r.status = status;
        r.assignedUserId = assignedUserId;
        r.assignedGuestId = assignedGuestId;
        r.qrContent = qrContent;
        r.qrBase64 = qrBase64;
        return r;
    }

    public Long getSeatId() { return seatId; }
    public String getStoreName() { return storeName; }
    public String getSeatNo() { return seatNo; }
    public String getCode() { return code; }
    public int getCapacity() { return capacity; }
    public SeatStatus getStatus() { return status; }
    public Long getAssignedUserId() { return assignedUserId; }
    public String getAssignedGuestId() { return assignedGuestId; }
    public String getQrContent() { return qrContent; }
    public String getQrBase64() { return qrBase64; }
}
