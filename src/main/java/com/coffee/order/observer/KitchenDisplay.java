package com.coffee.order.observer;

public class KitchenDisplay implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Long orderId, String beverageName, String newStatus) {
        if ("PREPARING".equals(newStatus)) {
            System.out.println("[厨房屏幕] 新制作任务 - 订单 #" + orderId
                    + ": " + beverageName);
        } else if ("COMPLETED".equals(newStatus)) {
            System.out.println("[厨房屏幕] 出品完成 - 订单 #" + orderId);
        }
    }

    @Override
    public String getObserverName() {
        return "厨房显示屏";
    }
}
