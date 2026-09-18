package com.coffee.module.payment.biz.application.service;

import com.coffee.module.payment.biz.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PaymentStateServiceTest {
    @Test
    void callbackMarksPendingOrProcessingOnlyOnce() {
        PaymentRepository repository = mock(PaymentRepository.class);
        PaymentStateService service = new PaymentStateService(repository);
        when(repository.markPaidIfPendingOrProcessing(9L, "MOCK", "txn-1", LocalDateTime.MIN))
                .thenReturn(true, false);

        assertTrue(service.markPaidIfPendingOrProcessing(9L, "MOCK", "txn-1", LocalDateTime.MIN));
        // A duplicate callback is represented by the repository's conditional update returning false.
        assertTrue(!service.markPaidIfPendingOrProcessing(9L, "MOCK", "txn-1", LocalDateTime.MIN));
        verify(repository, times(2)).markPaidIfPendingOrProcessing(9L, "MOCK", "txn-1", LocalDateTime.MIN);
    }
}
