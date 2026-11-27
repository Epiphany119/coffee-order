package com.coffee.web.agent;

import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerSupervisorIntentRouteTest {

    @Test
    void paymentWordsOpenCurrentPlanInsteadOfOrderHistory() {
        for (String message : List.of("支付", "去支付", "付款", "结算", "确认方案", "帮我支付这笔订单")) {
            assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                    CustomerSupervisorAgentOrchestrator.route(message, List.of(), true), message);
        }
    }

    @Test
    void confirmingRecommendationCreatesPlanThenPays() {
        List<String> recommendation = List.of(
                "user：给我推荐一杯不苦的冰咖啡",
                "assistant：根据您的偏好，我推荐卡布奇诺。中杯价格是28元，需要为你准备吗？");
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("好，就这个", recommendation, false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("好", recommendation, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("好，就这个", recommendation, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("可以，就它了", recommendation, false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("可以，就它了", recommendation, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("给我支付订单支付", recommendation, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("给我订单，我付款", recommendation, false));
    }

    @Test
    void followUpChoicesAndAdditionsStayInSamePlan() {
        List<String> sizeQuestion = List.of(
                "user：我还需要一个汉堡",
                "assistant：我们店有汉堡，小份10元、中份15元、大份20元。您需要什么份量的汉堡呢？");
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("2个10元的", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("2个10元的", sizeQuestion, false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("我还需要一个汉堡", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("饮品跟汉堡一起结算", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("饮品跟汉堡一起结账", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("再加一份可颂一起结账", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.REMOVE_ITEM,
                CustomerSupervisorAgentOrchestrator.route("不要汉堡了", sizeQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.REMOVE_ITEM,
                CustomerSupervisorAgentOrchestrator.route("清空方案", sizeQuestion, true));

        List<String> doneQuestion = List.of(
                "assistant：好的，为您准备2份小份汉堡，每份10元，共计20元。您还需要其他什么吗？");
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("暂时不用了", doneQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("不了了", doneQuestion, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("不用", doneQuestion, false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONFIRM,
                CustomerSupervisorAgentOrchestrator.route("不用", doneQuestion, true));

        List<String> priceAndTemperature = List.of(
                "user：来2杯手打柠檬茶",
                "assistant：请问您需要什么规格的柠檬茶？还有冰的还是常温的？小杯16元、中杯18元、大杯21元。");
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("18元，冰", priceAndTemperature, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("18元，冰", priceAndTemperature, false));
    }

    @Test
    void explicitQueryStillOpensOrderHistory() {
        for (String message : List.of("查一下我的订单", "查看订单记录", "我的订单", "订单状态", "物流到哪了")) {
            assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER_QUERY,
                    CustomerSupervisorAgentOrchestrator.route(message, List.of(), true), message);
        }
    }

    @Test
    void vagueOrderWordAsksForClarification() {
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CLARIFY,
                CustomerSupervisorAgentOrchestrator.route("订单", List.of(), true));
    }

    @Test
    void paymentQuestionsRemainConsultation() {
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONSULT,
                CustomerSupervisorAgentOrchestrator.route("支付方式有哪些", List.of(), true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONSULT,
                CustomerSupervisorAgentOrchestrator.route("怎么付款", List.of(), true));
    }

    @Test
    void dessertAddOnUsesRealMenuInsteadOfKnowledgeSnippets() {
        List<String> history = List.of(
                "user：我还想加点",
                "assistant：根据您想加甜点的需求，我们目前有以下甜点可供选择。");
        assertEquals("dessert", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("我还想加点", history));
        assertEquals("dessert", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("还有别的吗", history));
        assertEquals("", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("你们今天营业到几点", history));
        assertEquals("coffee", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("推荐一杯咖啡", history));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("选好了", history, true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("点好了", history, true));

        List<MenuItemDTO> products = List.of(
                product("latte", "拿铁", "coffee"),
                product("croissant", "法式可颂", "dessert"),
                product("tiramisu", "提拉米苏", "dessert"),
                product("macarons", "法式马卡龙", "dessert"));
        List<MenuItemDTO> candidates = CustomerSupervisorAgentOrchestrator.menuCandidates(products, "dessert");
        assertEquals(List.of("法式可颂", "提拉米苏", "法式马卡龙"),
                candidates.stream().map(MenuItemDTO::getName).toList());

        assertEquals(8, CustomerSupervisorAgentOrchestrator.menuOffset("还有别的吗",
                List.of("user：还有别的吗")));
        String answer = CustomerSupervisorAgentOrchestrator.menuListAnswer("dessert", candidates, 12, 0);
        assertTrue(answer.contains("提拉米苏"));
        assertTrue(answer.contains("¥18"));
        assertFalse(answer.contains("健达奇趣蛋"));
        assertFalse(answer.contains("黄金豆"));
    }

    private static MenuItemDTO product(String code, String name, String categoryCode) {
        MenuItemDTO item = new MenuItemDTO();
        item.setCode(code);
        item.setName(name);
        item.setCategoryCode(categoryCode);
        item.setBasePrice(18.0);
        item.setDescription("当前门店在售商品");
        return item;
    }

    @Test
    void recommendationAndExplicitOrderAreUnchanged() {
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONSULT,
                CustomerSupervisorAgentOrchestrator.route("推荐一杯最苦的咖啡", List.of(), false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("我要一杯拿铁", List.of(), false));
    }

    @Test
    void batchMultiSelectionAlwaysRoutesToOrderOrMerge() {
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("我要点：芝士蛋糕×1、提拉米苏×1、巧克力布朗尼×1", List.of(), false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("我要点：芝士蛋糕×1、提拉米苏×1", List.of(), true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("选好了：芝士蛋糕×1、提拉米苏×1、巧克力布朗尼×1", List.of(), false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("选好了：芝士蛋糕×1、提拉米苏×1", List.of(), true));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CHECKOUT,
                CustomerSupervisorAgentOrchestrator.route("选好了", List.of(), true));
    }

    @Test
    void sweetTasteModifierForDrinksDoesNotMisclassifyAsDessert() {
        assertEquals("drink", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("帮我推荐几种饮品，我喜欢甜点的", List.of()));
        assertEquals("coffee", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("给我推荐一杯甜点的咖啡", List.of()));
        assertEquals("dessert", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("有什么低糖甜点？", List.of()));
        assertEquals("dessert", CustomerSupervisorAgentOrchestrator.menuCategoryIntent("帮我推荐几款甜品", List.of()));

        List<MenuItemDTO> products = List.of(
                product("latte", "经典拿铁", "coffee"),
                product("peach_oolong", "蜜桃乌龙", "tea"),
                product("cheesecake", "芝士蛋糕", "dessert"),
                product("burger", "芝士汉堡", "food"));
        List<MenuItemDTO> drinks = CustomerSupervisorAgentOrchestrator.menuCandidates(products, "drink");
        assertEquals(List.of("经典拿铁", "蜜桃乌龙"),
                drinks.stream().map(MenuItemDTO::getName).toList());
    }
}
