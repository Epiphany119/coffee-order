package com.coffee.order.application.dto;

import java.time.LocalDateTime;

/**
 * 订单项 DTO
 */
public class OrderItemDTO {
    private String productCode;
    private String beverageName;
    private String categoryCode;
    private String size;
    private String condiments;
    private int quantity;
    private double unitPrice;
    private double subtotal;
    private String estimatedReadyTime;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getBeverageName() { return beverageName; }
    public void setBeverageName(String beverageName) { this.beverageName = beverageName; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getCondiments() { return condiments; }
    public void setCondiments(String condiments) { this.condiments = condiments; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public String getEstimatedReadyTime() { return estimatedReadyTime; }
    public void setEstimatedReadyTime(String estimatedReadyTime) { this.estimatedReadyTime = estimatedReadyTime; }
}
