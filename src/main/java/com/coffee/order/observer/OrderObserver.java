package com.coffee.order.observer;

/**
 * 观察者模式：订单观察者接口
 * 当订单状态发生变化时，所有注册的观察者都会收到通知
 */
public interface OrderObserver {
    void onOrderStatusChanged(Long orderId, String beverageName, String newStatus);
    String getObserverName();
}
