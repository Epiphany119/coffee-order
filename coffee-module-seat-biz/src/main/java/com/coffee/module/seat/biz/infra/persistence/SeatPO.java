package com.coffee.module.seat.biz.infra.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.coffee.module.seat.api.dto.SeatStatus;

import java.time.LocalDateTime;

/**
 * 座位持久化对象
 */
@TableName("seat")
public class SeatPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String storeName;
    private String seatNo;
    private Integer capacity;
    private SeatStatus status;
    private Long assignedUserId;
    private String assignedGuestId;
    private LocalDateTime assignedAt;
    private LocalDateTime occupiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
