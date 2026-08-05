package com.coffee.order.application.dto;

import java.util.List;

/**
 * 购物车项命令
 */
public class CartItemCommand {
    private String productCode;
    private String size;
    private List<String> condiments;
    private int quantity = 1;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public List<String> getCondiments() { return condiments; }
    public void setCondiments(List<String> condiments) { this.condiments = condiments; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
