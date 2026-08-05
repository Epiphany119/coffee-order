package com.coffee.order.dto;

import java.util.List;
import java.util.Map;

public class OrderResponse {
    private Long id;
    private String beverageName;       // 兼容旧字段：商品名+规格描述
    private double originalPrice;
    private double finalPrice;
    private String pricingStrategy;
    private String status;
    private String message;
    private double totalSpent;
    private String memberLevel;
    private int totalCups;
    private List<Map<String, Object>> items;
    private String categoryCode;       // 新增：所属类别
    private double memberDiscount;
    private double couponDiscount;
    private String couponName;
    private int earnedPoints;
    private String estimatedReadyTime;

    public OrderResponse() {}

    public OrderResponse(Long id, String beverageName, double originalPrice,
                         double finalPrice, String pricingStrategy, String status, String message) {
        this(id, beverageName, originalPrice, finalPrice, pricingStrategy, status, message, 0, "", "");
    }

    public OrderResponse(Long id, String beverageName, double originalPrice,
                         double finalPrice, String pricingStrategy, String status, String message,
                         double totalSpent, String memberLevel) {
        this(id, beverageName, originalPrice, finalPrice, pricingStrategy, status, message,
                totalSpent, memberLevel, "");
    }

    public OrderResponse(Long id, String beverageName, double originalPrice,
                         double finalPrice, String pricingStrategy, String status, String message,
                         double totalSpent, String memberLevel, String categoryCode) {
        this.id = id;
        this.beverageName = beverageName;
        this.originalPrice = originalPrice;
        this.finalPrice = finalPrice;
        this.pricingStrategy = pricingStrategy;
        this.status = status;
        this.message = message;
        this.totalSpent = totalSpent;
        this.memberLevel = memberLevel;
        this.categoryCode = categoryCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBeverageName() { return beverageName; }
    public void setBeverageName(String beverageName) { this.beverageName = beverageName; }
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
    public int getTotalCups() { return totalCups; }
    public void setTotalCups(int totalCups) { this.totalCups = totalCups; }
    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public double getMemberDiscount() { return memberDiscount; }
    public void setMemberDiscount(double memberDiscount) { this.memberDiscount = memberDiscount; }
    public double getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(double couponDiscount) { this.couponDiscount = couponDiscount; }
    public String getCouponName() { return couponName; }
    public void setCouponName(String couponName) { this.couponName = couponName; }
    public int getEarnedPoints() { return earnedPoints; }
    public void setEarnedPoints(int earnedPoints) { this.earnedPoints = earnedPoints; }
    public String getEstimatedReadyTime() { return estimatedReadyTime; }
    public void setEstimatedReadyTime(String estimatedReadyTime) { this.estimatedReadyTime = estimatedReadyTime; }
}
