package com.coffee.order.observer;

public class CustomerNotifier implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Long orderId, String beverageName, String newStatus) {
        System.out.println("[顾客通知] 您的订单 #" + orderId
                + " 状态已更新为: " + newStatus);
    }

    @Override
    public String getObserverName() {
        return "顾客通知服务";
    }
}
