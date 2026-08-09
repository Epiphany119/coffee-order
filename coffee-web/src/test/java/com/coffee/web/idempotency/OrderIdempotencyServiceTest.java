package com.coffee.web.idempotency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdempotencyServiceTest {

    @Test
    void shouldAcceptUuidCompatibleKey() {
        assertTrue(OrderIdempotencyService.isValidKey("49c1d8d9-5d69-4a15-a49e-2dbf08fafd11"));
    }

    @Test
    void shouldRejectUnsafeOrShortKey() {
        assertFalse(OrderIdempotencyService.isValidKey("short"));
        assertFalse(OrderIdempotencyService.isValidKey("bad key with spaces"));
        assertFalse(OrderIdempotencyService.isValidKey("bad\r\nkey-123456789"));
    }
}
