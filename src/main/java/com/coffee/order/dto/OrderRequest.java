package com.coffee.order.dto;

import java.util.List;

public class OrderRequest {
    private String productCode;        // 单杯模式：商品 code
    private String size;               // SINGLE_MODE：SMALL/MEDIUM/LARGE
    private List<String> condiments;
    private String pricingType;        // 兼容旧字段
    private Long userId;
    private String guestId;
    private List<CartItemRequest> items;  // 多杯购物车
    private String couponCode;            // 可选：每日可领优惠券
    private String fulfillmentType;       // PICKUP / DINE_IN
    private String note;                  // 门店备注

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public List<String> getCondiments() { return condiments; }
    public void setCondiments(List<String> condiments) { this.condiments = condiments; }
    public String getPricingType() { return pricingType; }
    public void setPricingType(String pricingType) { this.pricingType = pricingType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
    public List<CartItemRequest> getItems() { return items; }
    public void setItems(List<CartItemRequest> items) { this.items = items; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(String fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
