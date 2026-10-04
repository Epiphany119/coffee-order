package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.customeragent.api.dto.AgentOrderLine;
import com.coffee.module.customeragent.api.dto.AgentOrderPlan;
import com.coffee.module.inventory.api.InventoryService;
import com.coffee.module.menu.api.FavoriteService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.order.api.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerAgentPriceConfirmationTest {

    @Test
    void requiresNewCustomerConfirmationWhenMenuPriceChanged() {
        MenuService menu = mock(MenuService.class);
        InventoryService inventory = mock(InventoryService.class);
        CustomerAgentPlanRegistry registry = mock(CustomerAgentPlanRegistry.class);
        AgentOrderPlan plan = new AgentOrderPlan(7L, 42L, null,
                List.of(new AgentOrderLine("latte", "MEDIUM", 1, 101L, "燕麦拿铁", 32.0)), "推荐方案");
        when(registry.resolve("a1b2c3d4e5f60718293a4b5c6d7e8f90", 7L, 42L, null, false)).thenReturn(plan);
        when(registry.issue(any(AgentOrderPlan.class))).thenReturn("b1b2c3d4e5f60718293a4b5c6d7e8f90");
        MenuItemDTO current = new MenuItemDTO();
        current.setId(101L);
        current.setCode("latte");
        current.setName("燕麦拿铁");
        current.setAvailable(true);
        when(menu.getProductByCode(7L, "latte")).thenReturn(current);
        when(inventory.hasAvailable(7L, 101L, 1)).thenReturn(true);
        when(menu.calculatePrice(7L, "latte", "MEDIUM", null, List.of())).thenReturn(35.0);

        CustomerOrderAgentApplicationService service = new CustomerOrderAgentApplicationService(
                menu, mock(FavoriteService.class), mock(OrderService.class), inventory,
                mock(JdbcTemplate.class), mock(ZhipuChatClient.class), registry,
                mock(MenuPriceSelectionTool.class), new ObjectMapper(), mock(CandidateSetService.class),
                mock(LlmToolOrchestrator.class), "jingan");

        ServiceException error = assertThrows(ServiceException.class, () -> service.confirm(
                "a1b2c3d4e5f60718293a4b5c6d7e8f90", "agent-a1b2c3d4e5f60718293a4b5c6d7e8f90",
                7L, 42L, null, false));

        assertEquals(409, error.getCode());
        Map<?, ?> details = (Map<?, ?>) error.getData();
        assertEquals("AGENT_PLAN_PRICE_CHANGED", details.get("type"));
        assertEquals("b1b2c3d4e5f60718293a4b5c6d7e8f90", details.get("planToken"));
        assertEquals(35.0, ((Number) details.get("currentTotal")).doubleValue());
        verify(registry, never()).consume(anyString(), anyString(), any(), any(), any(), eq(false));
    }
}
