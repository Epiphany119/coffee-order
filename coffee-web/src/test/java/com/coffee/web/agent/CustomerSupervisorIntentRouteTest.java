package com.coffee.web.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.MERGE_ORDER,
                CustomerSupervisorAgentOrchestrator.route("饮品跟汉堡一起结算", sizeQuestion, true));

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
    void recommendationAndExplicitOrderAreUnchanged() {
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.CONSULT,
                CustomerSupervisorAgentOrchestrator.route("推荐一杯最苦的咖啡", List.of(), false));
        assertEquals(CustomerSupervisorAgentOrchestrator.Route.ORDER,
                CustomerSupervisorAgentOrchestrator.route("我要一杯拿铁", List.of(), false));
    }
}
