package com.coffee.order.interfaces.dto;

import com.coffee.order.application.dto.CartItemCommand;
import com.coffee.order.application.dto.CreateOrderCommand;

import java.util.List;

/**
 * 订单请求 DTO 适配器 - 将旧的 OrderRequest 转换为新的 CreateOrderCommand
 */
public class OrderRequestAdapter {

    public static CreateOrderCommand toCommand(OrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand();
        command.setUserId(request.getUserId());
        command.setGuestId(request.getGuestId());
        command.setProductCode(request.getProductCode());
        command.setSize(request.getSize());
        command.setCondiments(request.getCondiments());
        command.setCouponCode(request.getCouponCode());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<CartItemCommand> items = request.getItems().stream()
                    .map(OrderRequestAdapter::toCartItemCommand)
                    .toList();
            command.setItems(items);
        }

        return command;
    }

    private static CartItemCommand toCartItemCommand(CartItemRequest request) {
        CartItemCommand command = new CartItemCommand();
        command.setProductCode(request.getProductCode());
        command.setSize(request.getSize());
        command.setCondiments(request.getCondiments());
        command.setQuantity(request.getQuantity());
        return command;
    }

    public static class OrderRequest {
        private Long userId;
        private String guestId;
        private String productCode;
        private String size;
        private List<String> condiments;
        private List<CartItemRequest> items;
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
        public List<CartItemRequest> getItems() { return items; }
        public void setItems(List<CartItemRequest> items) { this.items = items; }
        public String getCouponCode() { return couponCode; }
        public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    }

    public static class CartItemRequest {
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
}
