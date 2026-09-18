package com.coffee.module.order.biz.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    @Test
    void successfulPublishClaimsAndMarksEventPublished() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "id", 7L, "event_id", "evt-7", "event_type", "OrderCompleted", "payload", "{}")));
        when(jdbc.update(contains("status = 'PUBLISHING'"), any(), eq(7L))).thenReturn(1);
        OutboxDispatcher dispatcher = new OutboxDispatcher(jdbc, new SimpleMeterRegistry(), publisher);

        dispatcher.dispatchPendingEvents();

        verify(publisher).publish("evt-7", "OrderCompleted", "{}");
        verify(jdbc).update(contains("status = 'PUBLISHED'"), any(), any(), eq(7L));
        verify(jdbc, never()).update(contains("attempts = attempts + 1"), any(), any(), any(), any());
    }

    @Test
    void failedPublishReturnsEventToPendingForRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 8L, "event_id", "evt-8", "event_type", "OrderPaid", "payload", "{}")));
        when(jdbc.update(contains("status = 'PUBLISHING'"), any(), eq(8L))).thenReturn(1);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher)
                .publish("evt-8", "OrderPaid", "{}");
        OutboxDispatcher dispatcher = new OutboxDispatcher(jdbc, new SimpleMeterRegistry(), publisher);

        dispatcher.dispatchPendingEvents();

        verify(jdbc).update(contains("status = 'PENDING'"), contains("broker unavailable"), any(), eq(8L));
        verify(jdbc, never()).update(contains("status = 'PUBLISHED'"), any(), any(), eq(8L));
    }
}
