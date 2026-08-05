package com.coffee.module.seat.biz.domain;

import com.coffee.module.seat.api.dto.SeatStatus;

import java.time.LocalDateTime;

/**
 * 座位领域实体
 */
public class Seat {

    private Long id;
    private String storeName;
    private String seatNo;
    private int capacity;
    private SeatStatus status;
    private Long assignedUserId;
    private String assignedGuestId;
    private LocalDateTime assignedAt;
    private LocalDateTime occupiedAt;

    /** 分配（FREE → ASSIGNED） */
    public void assignTo(Long userId, String guestId) {
        this.status = SeatStatus.ASSIGNED;
        this.assignedUserId = userId;
        this.assignedGuestId = guestId;
        this.assignedAt = LocalDateTime.now();
    }

    /** 落座（任意状态 → OCCUPIED），记录当前占用者 */
    public void occupy(Long userId, String guestId) {
        this.status = SeatStatus.OCCUPIED;
        this.assignedUserId = userId;
        this.assignedGuestId = guestId;
        this.occupiedAt = LocalDateTime.now();
    }

    /** 离座（OCCUPIED → FREE） */
    public void leave() {
        this.status = SeatStatus.FREE;
        this.assignedUserId = null;
        this.assignedGuestId = null;
        this.assignedAt = null;
        this.occupiedAt = null;
    }

    public String code() {
        return storeName + "-" + seatNo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public String getAssignedGuestId() { return assignedGuestId; }
    public void setAssignedGuestId(String assignedGuestId) { this.assignedGuestId = assignedGuestId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getOccupiedAt() { return occupiedAt; }
    public void setOccupiedAt(LocalDateTime occupiedAt) { this.occupiedAt = occupiedAt; }
}
