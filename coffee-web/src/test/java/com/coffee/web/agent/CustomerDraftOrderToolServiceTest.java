package com.coffee.web.agent;

import com.coffee.module.customeragent.api.CustomerOrderAgentService;
import com.coffee.module.inventory.api.InventoryService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class CustomerDraftOrderToolServiceTest {

    private CustomerSessionDraftService draftService;
    private CustomerOrderAgentService orderAgent;
    private MenuService menuService;
    private InventoryService inventoryService;
    private CustomerDraftOrderToolService toolService;

    private MenuItemDTO jasmineTea;
    private MenuItemDTO burger;

    @BeforeEach
    void setUp() {
        draftService = new CustomerSessionDraftService(new ObjectMapper());
        orderAgent = Mockito.mock(CustomerOrderAgentService.class);
        menuService = Mockito.mock(MenuService.class);
        inventoryService = Mockito.mock(InventoryService.class);
        toolService = new CustomerDraftOrderToolService(draftService, orderAgent, menuService, inventoryService);

        jasmineTea = new MenuItemDTO();
        jasmineTea.setId(101L);
        jasmineTea.setCode("jasmine_tea");
        jasmineTea.setName("茉莉花茶");
        jasmineTea.setCategoryCode("tea");
        jasmineTea.setTemperature("COLD");
        jasmineTea.setAvailable(true);
        jasmineTea.setBasePrice(16.0);
        jasmineTea.setPriceSmall(14.0);
        jasmineTea.setPriceMedium(16.0);
        jasmineTea.setPriceLarge(19.0);

        burger = new MenuItemDTO();
        burger.setId(202L);
        burger.setCode("beef_burger");
        burger.setName("汉堡");
        burger.setCategoryCode("food");
        burger.setTemperature("HOT");
        burger.setAvailable(true);
        burger.setBasePrice(15.0);
        burger.setPriceSmall(10.0);
        burger.setPriceMedium(15.0);
        burger.setPriceLarge(20.0);

        when(menuService.getAllProducts(1L)).thenReturn(List.of(jasmineTea, burger));
        when(inventoryService.hasAvailable(eq(1L), anyLong(), anyInt())).thenReturn(true);
        when(menuService.calculatePrice(eq(1L), eq("jasmine_tea"), anyString(), isNull(), anyList()))
                .thenAnswer(inv -> switch (String.valueOf(inv.getArgument(2))) {
                    case "SMALL" -> 14.0;
                    case "LARGE" -> 19.0;
                    default -> 16.0;
                });
        when(menuService.calculatePrice(eq(1L), eq("beef_burger"), anyString(), isNull(), anyList()))
                .thenAnswer(inv -> switch (String.valueOf(inv.getArgument(2))) {
                    case "SMALL" -> 10.0;
                    case "LARGE" -> 20.0;
                    default -> 15.0;
                });

        AtomicInteger tokenSeq = new AtomicInteger(1);
        when(orderAgent.rebuild(eq(1L), any(), any(), any(), anyList(), anyBoolean(), anyString()))
                .thenAnswer(inv -> {
                    List<Map<String, Object>> lines = inv.getArgument(4);
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (Map<String, Object> line : lines) {
                        String code = String.valueOf(line.get("productCode"));
                        String size = String.valueOf(line.getOrDefault("size", "MEDIUM"));
                        int qty = ((Number) line.getOrDefault("quantity", 1)).intValue();
                        MenuItemDTO p = "beef_burger".equals(code) ? burger : jasmineTea;
                        double unitPrice = menuService.calculatePrice(1L, code, size, null, List.of());
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("productId", p.getId());
                        item.put("productCode", p.getCode());
                        item.put("name", p.getName());
                        item.put("categoryCode", p.getCategoryCode());
                        item.put("temperature", p.getTemperature());
                        item.put("size", size);
                        item.put("quantity", qty);
                        item.put("estimatedPrice", unitPrice);
                        item.put("condiments", List.of());
                        items.add(item);
                    }
                    Map<String, Object> plan = new LinkedHashMap<>();
                    plan.put("planToken", "PLAN-" + tokenSeq.getAndIncrement());
                    plan.put("items", items);
                    plan.put("expiresInSeconds", 300);
                    return plan;
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullMultiTurnOrderingMergingReviewAndClearFlow() {
        String sessionId = "sess-1";
        String owner = "USER:7|STORE:1";

        // 1. “来杯茉莉花茶” -> 严格 1 份，不会膨胀成 2 份
        var step1 = toolService.addByUserInput(sessionId, owner, 1L, 7L, null, "来杯茉莉花茶", List.of(), false);
        assertTrue(step1.matched());
        assertEquals("ORDER_PLAN", step1.actionType());
        Map<String, Object> payload1 = (Map<String, Object>) step1.payload();
        List<Map<String, Object>> items1 = (List<Map<String, Object>>) payload1.get("items");
        assertEquals(1, items1.size());
        assertEquals(1, items1.get(0).get("quantity"));
        assertEquals(16.0, ((Map<String, Object>) payload1.get("pricing")).get("itemsSubtotal"));

        // 2. “我还需要一个汉堡” -> 多档价位，下发规格选择卡并暂存待选商品
        var step2 = toolService.addByUserInput(sessionId, owner, 1L, 7L, null, "我还需要一个汉堡", List.of(), true);
        assertTrue(step2.matched());
        assertEquals("ASK_CHOICE", step2.actionType());
        assertTrue(step2.answer().contains("小份 ¥10"));

        // 3. “2个10元的” -> 反推小份汉堡 × 2，与茉莉花茶合并，总计 16 + 20 = 36 元
        var step3 = toolService.addByUserInput(sessionId, owner, 1L, 7L, null, "2个10元的", List.of(), false);
        assertTrue(step3.matched());
        assertEquals("ORDER_PLAN", step3.actionType());
        Map<String, Object> payload3 = (Map<String, Object>) step3.payload();
        List<Map<String, Object>> items3 = (List<Map<String, Object>>) payload3.get("items");
        assertEquals(2, items3.size());
        assertEquals(36.0, ((Map<String, Object>) payload3.get("pricing")).get("itemsSubtotal"));
        assertEquals(36.0, ((Map<String, Object>) payload3.get("pricing")).get("estimatedPayable"));

        // 4. “饮品跟汉堡一起结账” -> 结账预览返回完整 2 行商品与 ¥36.00，状态 REVIEW_READY
        var step4 = toolService.previewCheckout(sessionId, owner, 1L, 7L, null, null);
        assertTrue(step4.matched());
        assertEquals("ORDER_PLAN", step4.actionType());
        Map<String, Object> payload4 = (Map<String, Object>) step4.payload();
        assertEquals("REVIEW_READY", payload4.get("draftStatus"));
        assertEquals(36.0, ((Map<String, Object>) payload4.get("pricing")).get("itemsSubtotal"));

        // 5. “不要汉堡了” -> 移除汉堡，仅保留茉莉花茶 ¥16.00
        var step5 = toolService.removeByText(sessionId, owner, 1L, 7L, null, "不要汉堡了");
        assertTrue(step5.matched());
        assertEquals("ORDER_PLAN", step5.actionType());
        Map<String, Object> payload5 = (Map<String, Object>) step5.payload();
        List<Map<String, Object>> items5 = (List<Map<String, Object>>) payload5.get("items");
        assertEquals(1, items5.size());
        assertEquals("jasmine_tea", items5.get(0).get("productCode"));
        assertEquals(16.0, ((Map<String, Object>) payload5.get("pricing")).get("itemsSubtotal"));

        // 6. 批量多选一次提交（“选好了：茉莉花茶×2、汉堡×1”）-> 一次合并入草稿单并直接进入 REVIEW_READY
        draftService.clear(sessionId, owner, 1L);
        var batch = toolService.addByUserInput(sessionId, owner, 1L, 7L, null,
                "选好了：茉莉花茶×2、汉堡×1", List.of(), false);
        assertTrue(batch.matched());
        assertEquals("ORDER_PLAN", batch.actionType());
        Map<String, Object> batchPayload = (Map<String, Object>) batch.payload();
        assertEquals("REVIEW_READY", batchPayload.get("draftStatus"));
        List<Map<String, Object>> batchItems = (List<Map<String, Object>>) batchPayload.get("items");
        assertEquals(2, batchItems.size());
        assertEquals(47.0, ((Map<String, Object>) batchPayload.get("pricing")).get("itemsSubtotal"));

        // 7. 确认下单后标记 SUBMITTED，草稿单不再有活动商品
        draftService.markSubmitted(sessionId, owner, 1L);
        assertFalse(draftService.hasActiveItems(sessionId, owner, 1L));
    }
}
