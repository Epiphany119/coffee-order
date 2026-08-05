package com.coffee.order.application.dto;

import java.util.List;

/**
 * 订单创建请求
 */
public class CreateOrderCommand {
    private Long userId;
    private String guestId;
    private String productCode;
    private String size;
    private List<String> condiments;
    private List<CartItemCommand> items;
    private String couponCode;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public List<String> getCondiments() { return condiments; }
    public void setCondiments(List<String> condiments) { this.condiments = condiments; }
    public List<CartItemCommand> getItems() { return items; }
    public void setItems(List<CartItemCommand> items) { this.items = items; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public boolean isBatch() {
        return items != null && !items.isEmpty();
    }
}
