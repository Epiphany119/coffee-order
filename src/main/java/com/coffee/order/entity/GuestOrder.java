package com.coffee.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "guest_order")
public class GuestOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String guestId;

    @Column(nullable = false, length = 100)
    private String beverageName;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(length = 200)
    private String condiments;

    @Column(nullable = false)
    private double originalPrice;

    @Column(nullable = false)
    private double finalPrice;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime estimatedReadyTime;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
    public String getBeverageName() { return beverageName; }
    public void setBeverageName(String beverageName) { this.beverageName = beverageName; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getCondiments() { return condiments; }
    public void setCondiments(String condiments) { this.condiments = condiments; }
    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getEstimatedReadyTime() { return estimatedReadyTime; }
    public void setEstimatedReadyTime(LocalDateTime estimatedReadyTime) { this.estimatedReadyTime = estimatedReadyTime; }
}
