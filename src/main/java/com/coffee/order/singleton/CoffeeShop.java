package com.coffee.order.singleton;

import com.coffee.order.entity.UserOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单例模式：咖啡店全局唯一实例
 * 使用双重检查锁定（DCL）保证线程安全
 * 整个系统只有一个咖啡店管理所有订单
 */
public class CoffeeShop {
    private static volatile CoffeeShop instance;
    private final List<UserOrder> orders = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final String shopName;

    private CoffeeShop() {
        this.shopName = "设计模式咖啡馆";
    }

    public static CoffeeShop getInstance() {
        if (instance == null) {
            synchronized (CoffeeShop.class) {
                if (instance == null) {
                    instance = new CoffeeShop();
                }
            }
        }
        return instance;
    }

    public void addOrder(UserOrder order) {
        orders.add(order);
    }

    public List<UserOrder> getAllOrders() {
        return orders;
    }

    public UserOrder getOrderById(Long id) {
        return orders.stream()
                .filter(o -> o.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public String getShopName() {
        return shopName;
    }
}
