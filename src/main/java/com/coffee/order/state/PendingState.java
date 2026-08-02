package com.coffee.order.state;

public class PendingState implements OrderState {
    @Override
    public String next(String action) {
        return switch (action) {
            case "prepare" -> "PREPARING";
            case "cancel" -> "CANCELED";
            default -> "PENDING";
        };
    }

    @Override
    public String getStatusName() { return "PENDING"; }
}
