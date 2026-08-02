package com.coffee.order.state;

import java.util.HashMap;
import java.util.Map;

public class OrderStateContext {
    private static final Map<String, OrderState> STATE_MAP = new HashMap<>();

    static {
        STATE_MAP.put("PENDING", new PendingState());
        STATE_MAP.put("PREPARING", new PreparingState());
        STATE_MAP.put("COMPLETED", new CompletedState());
        STATE_MAP.put("CANCELED", new CanceledState());
    }

    public static OrderState getState(String status) {
        return STATE_MAP.get(status);
    }

    public static String nextState(String currentStatus, String action) {
        OrderState state = STATE_MAP.get(currentStatus);
        return state != null ? state.next(action) : currentStatus;
    }
}
