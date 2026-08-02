package com.coffee.order.dto;

import java.util.List;

public class CartItemRequest {
    private String productCode;        // 新：商品 code (espresso / jasmine / tiramisu ...)
    private String size;               // SMALL/MEDIUM/LARGE，甜点轻食用 MEDIUM
    private List<String> condiments;   // 甜点轻食=null
    private int quantity;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public List<String> getCondiments() { return condiments; }
    public void setCondiments(List<String> condiments) { this.condiments = condiments; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
