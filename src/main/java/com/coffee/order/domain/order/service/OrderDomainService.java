package com.coffee.order.domain.order.service;

import com.coffee.order.domain.order.aggregate.OrderAggregate;
import com.coffee.order.domain.order.entity.OrderStatus;
import com.coffee.order.domain.order.event.OrderDomainEvent;
import com.coffee.order.domain.order.valueobject.OrderLineItem;
import com.coffee.order.domain.order.valueobject.Money;
import com.coffee.order.domain.product.entity.Product;
import com.coffee.order.domain.product.service.ProductDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单领域服务 - 包含核心业务逻辑
 */
@Service
public class OrderDomainService {

    public static final int DEFAULT_PREPARE_MINUTES = 12;

    private final ProductDomainService productDomainService;

    public OrderDomainService(ProductDomainService productDomainService) {
        this.productDomainService = productDomainService;
    }

    public OrderAggregate createOrder(
            Long userId,
            String guestId,
            List<OrderLineItem> lineItems,
            Money money) {
        OrderAggregate order;
        if (userId != null && userId > 0) {
            order = OrderAggregate.createForUser(userId, lineItems, money);
        } else {
            order = OrderAggregate.createForGuest(guestId != null ? guestId : "anonymous", lineItems, money);
        }
        return order;
    }

    public OrderLineItem assembleLineItem(
            String productCode,
            String size,
            List<String> condiments,
            int quantity) {
        Product product = productDomainService.findProductByCode(productCode);
        double unitPrice = productDomainService.calculatePrice(product, size, condiments);
        String fullName = productDomainService.getDisplayName(product, size, condiments);
        LocalDateTime readyTime = LocalDateTime.now().plusMinutes(DEFAULT_PREPARE_MINUTES);

        return OrderLineItem.create(
                productCode,
                fullName,
                product.getCategoryCode(),
                size,
                condiments,
                quantity,
                unitPrice,
                readyTime
        );
    }

    public OrderDomainEvent whenOrderCreated(OrderAggregate order) {
        return OrderDomainEvent.orderCreated(order.getId(), order.getBeverageName(), order.getStatus());
    }

    public OrderDomainEvent whenOrderStatusChanged(OrderAggregate order, OrderStatus newStatus) {
        order.transitionTo(newStatus);
        return OrderDomainEvent.orderStatusChanged(order.getId(), order.getBeverageName(), newStatus);
    }

    public OrderDomainEvent whenOrderReady(OrderAggregate order) {
        order.transitionTo(OrderStatus.COMPLETED);
        return OrderDomainEvent.orderReady(order.getId(), order.getBeverageName());
    }

    public OrderStatus calculateNextStatus(String currentStatus, String action) {
        return switch (currentStatus) {
            case "PENDING" -> action.equals("start") ? OrderStatus.PREPARING : OrderStatus.PENDING;
            case "PREPARING" -> action.equals("complete") ? OrderStatus.COMPLETED :
                               action.equals("cancel") ? OrderStatus.CANCELED : OrderStatus.PREPARING;
            default -> OrderStatus.valueOf(currentStatus);
        };
    }

    public double calculateMemberDiscount(Long userId, double totalOriginal, double discountRate) {
        return Math.round(totalOriginal * (1 - discountRate) * 100.0) / 100.0;
    }

    public int calculateEarnedPoints(double paidAmount) {
        return Math.max(0, (int) Math.floor(paidAmount));
    }
}
