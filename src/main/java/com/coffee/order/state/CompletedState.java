package com.coffee.order.state;

public class CompletedState implements OrderState {
    @Override
    public String next(String action) {
        return "COMPLETED";
    }

    @Override
    public String getStatusName() { return "COMPLETED"; }
}
