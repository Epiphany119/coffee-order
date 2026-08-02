package com.coffee.order.state;

/**
 * 状态模式：订单状态接口
 * 将订单的每个状态封装为独立的类，状态转换由状态对象自身决定
 */
public interface OrderState {
    String next(String action);
    String getStatusName();
}
