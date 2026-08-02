package com.coffee.order.state;

public class PreparingState implements OrderState {
    @Override
    public String next(String action) {
        return "complete".equals(action) ? "COMPLETED" : "PREPARING";
    }

    @Override
    public String getStatusName() { return "PREPARING"; }
}
