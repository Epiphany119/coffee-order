package com.coffee.order.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单响应 DTO
 */
public class OrderResponseDTO {
    private Long orderId;
    private String orderName;
    private double originalPrice;
    private double finalPrice;
    private String pricingStrategy;
    private String status;
    private String message;
    private double totalSpent;
    private String memberLevel;
    private String categoryCode;
    private Integer totalCups;
    private List<OrderItemDTO> items;
    private double memberDiscount;
    private double couponDiscount;
    private String couponName;
    private Integer earnedPoints;
    private String estimatedReadyTime;
    private LocalDateTime createdAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderName() { return orderName; }
    public void setOrderName(String orderName) { this.orderName = orderName; }
    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
    public String getPricingStrategy() { return pricingStrategy; }
    public void setPricingStrategy(String pricingStrategy) { this.pricingStrategy = pricingStrategy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public Integer getTotalCups() { return totalCups; }
    public void setTotalCups(Integer totalCups) { this.totalCups = totalCups; }
    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }
    public double getMemberDiscount() { return memberDiscount; }
    public void setMemberDiscount(double memberDiscount) { this.memberDiscount = memberDiscount; }
    public double getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(double couponDiscount) { this.couponDiscount = couponDiscount; }
    public String getCouponName() { return couponName; }
    public void setCouponName(String couponName) { this.couponName = couponName; }
    public Integer getEarnedPoints() { return earnedPoints; }
    public void setEarnedPoints(Integer earnedPoints) { this.earnedPoints = earnedPoints; }
    public String getEstimatedReadyTime() { return estimatedReadyTime; }
    public void setEstimatedReadyTime(String estimatedReadyTime) { this.estimatedReadyTime = estimatedReadyTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
