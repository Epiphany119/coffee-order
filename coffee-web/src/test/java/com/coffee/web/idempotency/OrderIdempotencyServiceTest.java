package com.coffee.web.idempotency;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldReplaySuccessBeforeRunningOrderCreator() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        OrderIdempotencyService service = new OrderIdempotencyService(jdbc, mapper);
        ConfirmationFingerprint fingerprint = new ConfirmationFingerprint(
                "a1b2c3d4e5f60718293a4b5c6d7e8f90", 7L, "PICKUP", null, false);
        String key = "agent-a1b2c3d4e5f60718293a4b5c6d7e8f90";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(fingerprint)));
        OrderResponse expected = new OrderResponse();
        expected.setOrderId(918L);
        expected.setOrderNo("260102-123456-007-001");
        expected.setFinalPrice(32.0);
        Map<String, Object> row = Map.of("request_hash", hash, "status", "SUCCESS",
                "response_body", mapper.writeValueAsString(expected));
        doThrow(new DuplicateKeyException("already claimed"))
                .when(jdbc).update(anyString(), any(Object[].class));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        AtomicInteger creatorCalls = new AtomicInteger();

        OrderResponse replay = service.executeWithRequest(key, 42L, null, fingerprint, () -> {
            creatorCalls.incrementAndGet();
            return new OrderResponse();
        });

        assertEquals(918L, replay.getOrderId());
        assertEquals(32.0, replay.getFinalPrice());
        assertEquals(0, creatorCalls.get());
    }

    @Test
    void shouldRejectChangedFingerprintForAnExistingKey() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        OrderIdempotencyService service = new OrderIdempotencyService(jdbc, mapper);
        ConfirmationFingerprint original = new ConfirmationFingerprint(
                "a1b2c3d4e5f60718293a4b5c6d7e8f90", 7L, "PICKUP", null, false);
        String originalHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(original)));
        doThrow(new DuplicateKeyException("already claimed"))
                .when(jdbc).update(anyString(), any(Object[].class));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "request_hash", originalHash, "status", "SUCCESS", "response_body", "{}")));

        assertThrows(ServiceException.class, () -> service.executeWithRequest(
                "agent-a1b2c3d4e5f60718293a4b5c6d7e8f90", 42L, null,
                new ConfirmationFingerprint(original.planToken(), 8L, "PICKUP", null, false), OrderResponse::new));
    }

    private record ConfirmationFingerprint(String planToken, Long storeId, String fulfillmentType,
                                          Long deliveryAddressId, boolean includeAddOn) { }
}
