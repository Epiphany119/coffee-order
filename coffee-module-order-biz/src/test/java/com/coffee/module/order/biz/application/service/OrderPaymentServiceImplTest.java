package com.coffee.module.order.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentServiceImplTest {
    @Test
    void canceledOrderCannotBePaid() {
        OrderRepository repository = mock(OrderRepository.class);
        Order order = new Order();
        order.setStatus(Order.OrderStatus.CANCELED);
        when(repository.findById(1L)).thenReturn(order);

        OrderPaymentServiceImpl service = new OrderPaymentServiceImpl(repository);
        assertFalse(service.markPaid(1L));
        verify(repository, never()).updateStatusIfCurrent(anyLong(), any(), any());
    }

    @Test
    void unpaidOrderUsesCompareAndSetToPreventDuplicatePaymentTransition() {
        OrderRepository repository = mock(OrderRepository.class);
        Order order = new Order();
        order.setStatus(Order.OrderStatus.UNPAID);
        when(repository.findById(2L)).thenReturn(order);
        when(repository.updateStatusIfCurrent(2L, Order.OrderStatus.UNPAID, Order.OrderStatus.PENDING))
                .thenReturn(true);

        assertTrue(new OrderPaymentServiceImpl(repository).markPaid(2L));
        verify(repository).updateStatusIfCurrent(2L, Order.OrderStatus.UNPAID, Order.OrderStatus.PENDING);
    }
}
