package com.coffee.web.order;

import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.service.OrderDomainService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDomainServiceTest {

    private final OrderDomainService service = new OrderDomainService();

    @Test
    void deliveryOrderMustFollowMerchantThenRiderFlow() {
        assertEquals(Order.OrderStatus.ACCEPTED,
                service.calculateNextStatus("PENDING", "accept", "DELIVERY"));
        assertEquals(Order.OrderStatus.PREPARING,
                service.calculateNextStatus("ACCEPTED", "start", "DELIVERY"));
        assertEquals(Order.OrderStatus.READY_FOR_DELIVERY,
                service.calculateNextStatus("PREPARING", "complete", "DELIVERY"));
    }

    @Test
    void pickupOrderCompletesAfterMerchantFinishes() {
        assertEquals(Order.OrderStatus.COMPLETED,
                service.calculateNextStatus("PREPARING", "complete", "PICKUP"));
        assertEquals(Order.OrderStatus.COMPLETED,
                service.calculateNextStatus("PREPARING", "complete", "DINE_IN"));
    }

    @Test
    void illegalMerchantTransitionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateNextStatus("PENDING", "start", "DELIVERY"));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateNextStatus("READY_FOR_DELIVERY", "complete", "DELIVERY"));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateNextStatus("DELIVERING", "cancel", "DELIVERY"));
    }
}
