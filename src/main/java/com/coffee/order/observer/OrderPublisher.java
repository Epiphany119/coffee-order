package com.coffee.order.observer;

import java.util.ArrayList;
import java.util.List;

public class OrderPublisher {
    private final List<OrderObserver> observers = new ArrayList<>();

    public void subscribe(OrderObserver observer) {
        observers.add(observer);
    }

    public void unsubscribe(OrderObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Long orderId, String beverageName, String newStatus) {
        for (OrderObserver observer : observers) {
            observer.onOrderStatusChanged(orderId, beverageName, newStatus);
        }
    }

    public List<String> getObserverNames() {
        return observers.stream()
                .map(OrderObserver::getObserverName)
                .toList();
    }
}
