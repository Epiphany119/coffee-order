package com.coffee.order.state;

public class CanceledState implements OrderState {
    @Override
    public String next(String action) {
        return "CANCELED";
    }

    @Override
    public String getStatusName() { return "CANCELED"; }
}
