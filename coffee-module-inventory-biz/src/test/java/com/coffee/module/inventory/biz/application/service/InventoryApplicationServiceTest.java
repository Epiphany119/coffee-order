package com.coffee.module.inventory.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryApplicationServiceTest {
    private JdbcTemplate jdbc;
    private InventoryApplicationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new InventoryApplicationService(jdbc, mock(StringRedisTemplate.class), false);
    }

    @Test
    void reserveUsesConditionalUpdateAndRejectsInsufficientStock() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(1L, 2L, 3));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("库存不足"));
        verify(jdbc).update(contains("available_stock >="), eq(3), eq(3), eq(1L), eq(2L), eq(3));
    }

    @Test
    void invalidReserveArgumentsFailBeforeDatabaseAccess() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reserve(1L, 2L, 0));
        assertEquals(400, error.getCode());
        verifyNoInteractions(jdbc);
    }
}
