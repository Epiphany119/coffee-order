package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerOrderLlmIntentParserTest {
    @Test
    void rawTextWinsOverHallucinatedExampleConstraints() {
        ZhipuChatClient chatClient = new ZhipuChatClient(new ObjectMapper(), "", "http://localhost", "test", 1) {
            @Override
            public Optional<String> callJson(String systemPrompt, String userPrompt, int maxTokens) {
                return Optional.of("""
                        {"target_count":4,"budget_max":70,"tags":["冰饮"],
                         "must_include":["冰沙","冰沙","蛋糕"],"avoid":["咖啡"],
                         "temperature":"hot","expect_combination":"mix"}
                        """);
            }

            @Override
            public String extractJson(String raw) {
                return raw;
            }
        };

        CustomerOrderIntentParser.Intent intent = new CustomerOrderLlmIntentParser(chatClient, new ObjectMapper())
                .parse("两杯冰沙和一个蛋糕，预算30元");

        assertEquals(30, intent.budget());
        assertEquals(3, intent.itemCount());
        assertEquals(CustomerOrderIntentParser.Temperature.COLD, intent.temperature());
        assertTrue(intent.requiredCategories().contains("ice"));
        assertTrue(intent.requiredCategories().contains("dessert"));
        assertFalse(intent.requiredCategories().contains("food"));
        assertFalse(intent.requiredCategories().contains("coffee"));
    }

    @Test
    void exactProductMentionDoesNotBypassCategoryConstraint() {
        MenuItemDTO coffee = new MenuItemDTO();
        coffee.setName("燕麦拿铁");
        coffee.setCategoryCode("coffee");
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser.Intent(
                java.util.List.of("food"), java.util.Set.of(), java.util.Set.of(),
                CustomerOrderIntentParser.Temperature.ANY, null, false, 1,
                CustomerOrderIntentParser.PricePreference.NONE, "", java.util.List.of("燕麦拿铁"));

        IntentValidator.ValidationResult result = new IntentValidator()
                .validateCombo(java.util.List.of(coffee), intent, 10);

        assertFalse(result.valid());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("轻食")));
    }

    @Test
    void fallbackAlsoCountsMultipleQuantityPhrases() {
        ZhipuChatClient chatClient = new ZhipuChatClient(new ObjectMapper(), "", "http://localhost", "test", 1) {
            @Override
            public Optional<String> callJson(String systemPrompt, String userPrompt, int maxTokens) {
                return Optional.empty();
            }
        };

        CustomerOrderIntentParser.Intent intent = new CustomerOrderLlmIntentParser(chatClient, new ObjectMapper())
                .parse("两杯冰沙和一个蛋糕");

        assertEquals(3, intent.itemCount());
    }

    @Test
    void fallbackKeepsExplicitQuantityBeforePreferenceWords() {
        ZhipuChatClient chatClient = new ZhipuChatClient(new ObjectMapper(), "", "http://localhost", "test", 1) {
            @Override
            public Optional<String> callJson(String systemPrompt, String userPrompt, int maxTokens) {
                return Optional.empty();
            }
        };

        CustomerOrderIntentParser.Intent intent = new CustomerOrderLlmIntentParser(chatClient, new ObjectMapper())
                .parse("我想喝一杯甜一点的");

        assertEquals(1, intent.itemCount());
        assertTrue(intent.requiredCategories().contains(CustomerOrderIntentCatalog.DRINK_CATEGORY));
        assertTrue(intent.preferenceTags().contains("sweet"));
    }

    @Test
    void unspecifiedQuantityDefaultsToOneItemNotThree() {
        ZhipuChatClient chatClient = new ZhipuChatClient(new ObjectMapper(), "", "http://localhost", "test", 1) {
            @Override
            public Optional<String> callJson(String systemPrompt, String userPrompt, int maxTokens) {
                return Optional.empty();
            }
        };

        CustomerOrderIntentParser.Intent intent = new CustomerOrderLlmIntentParser(chatClient, new ObjectMapper())
                .parse("推荐一款清爽的饮品");

        assertEquals(1, intent.itemCount());
        assertTrue(intent.requiredCategories().contains(CustomerOrderIntentCatalog.DRINK_CATEGORY));
    }

    @Test
    void recommendationVarietyCountDoesNotBecomeOrderQuantity() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("推荐三款清爽的饮品");

        assertEquals(1, intent.itemCount());
        assertTrue(intent.requiredCategories().contains(CustomerOrderIntentCatalog.DRINK_CATEGORY));
    }

    @Test
    void candidateRestrictionPreservesUnifiedRankingOrder() {
        MenuItemDTO first = product(1L, "first", "普通饮品", "coffee");
        MenuItemDTO second = product(2L, "second", "甜饮", "coffee");
        CandidateSetService.CandidateResult result = new CandidateSetService.CandidateResult(
                List.of(first, second), Map.of("first", 0.1, "second", 0.9), Set.of(), Set.of(), Set.of());

        assertEquals(List.of("second", "first"), result.restrictTo(List.of(second, first))
                .candidates().stream().map(MenuItemDTO::getCode).toList());
    }

    @Test
    void unifiedPolicyUsesPreferenceTagsWhenSemanticScoresUnavailable() {
        MenuItemDTO plain = product(1L, "plain", "美式咖啡", "coffee");
        MenuItemDTO sweet = product(2L, "sweet", "焦糖拿铁 甜饮", "coffee");
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser.Intent(
                List.of(CustomerOrderIntentCatalog.DRINK_CATEGORY), Set.of(), Set.of(),
                CustomerOrderIntentParser.Temperature.ANY, null, false, 1,
                CustomerOrderIntentParser.PricePreference.NONE, "", List.of(), List.of("sweet"));

        List<MenuItemDTO> ranked = new CustomerOrderRecommendationPolicy().rank(
                List.of(plain, sweet), "我想喝甜一点的", Set.of(), Set.of(), Set.of(),
                Map.of(), Map.of(), intent, Map.of());

        assertEquals("sweet", ranked.get(0).getCode());
    }

    @Test
    void excludedQuantityIsNotCountedAsOrderQuantity() {
        ZhipuChatClient chatClient = new ZhipuChatClient(new ObjectMapper(), "", "http://localhost", "test", 1) {
            @Override
            public Optional<String> callJson(String systemPrompt, String userPrompt, int maxTokens) {
                return Optional.empty();
            }
        };

        CustomerOrderIntentParser.Intent intent = new CustomerOrderLlmIntentParser(chatClient, new ObjectMapper())
                .parse("不要一杯咖啡，再来一杯茶");

        assertEquals(1, intent.itemCount());
        assertTrue(intent.excludedCategories().contains("coffee"));
        assertTrue(intent.requiredCategories().contains("tea"));
    }

    @Test
    void negativePreferenceDoesNotBecomePositivePreference() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser().parse("我想喝不甜的饮品");

        assertTrue(intent.preferenceTags().contains("low_sugar"));
        assertFalse(intent.preferenceTags().contains("sweet"));
    }

    @Test
    void commonPreferenceNegationIsHandledByTheSameCatalog() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("我不喜欢太苦的热咖啡");

        assertTrue(intent.requiredCategories().contains("coffee"));
        assertEquals(CustomerOrderIntentParser.Temperature.HOT, intent.temperature());
        assertTrue(intent.preferenceTags().contains("avoid_bitter"));
        assertFalse(intent.preferenceTags().contains("bitter"));
    }

    @Test
    void negatingTasteDoesNotExcludeTheWholeDrinkCategory() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("我不想喝甜的饮品");

        assertTrue(intent.requiredCategories().contains(CustomerOrderIntentCatalog.DRINK_CATEGORY));
        assertFalse(intent.excludedCategories().contains(CustomerOrderIntentCatalog.DRINK_CATEGORY));
        assertTrue(intent.preferenceTags().contains("avoid_sweet"));
    }

    @Test
    void specificProductExclusionDoesNotExcludeItsWholeCategory() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("不要拿铁");

        assertFalse(intent.requiredCategories().contains("coffee"));
        assertFalse(intent.excludedCategories().contains("coffee"));
        assertTrue(intent.excludedProducts().contains("拿铁"));
    }

    @Test
    void temperatureNegationUsesThePositiveTemperature() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("不要冰的，给我热的咖啡");

        assertEquals(CustomerOrderIntentParser.Temperature.HOT, intent.temperature());
        assertTrue(intent.requiredCategories().contains("coffee"));
    }

    @Test
    void sweetFoodContextMeansDessertInsteadOfGenericFood() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser()
                .parse("我想吃点甜的");

        assertTrue(intent.requiredCategories().contains("dessert"));
        assertFalse(intent.requiredCategories().contains("food"));
    }

    private MenuItemDTO product(Long id, String code, String name, String category) {
        MenuItemDTO product = new MenuItemDTO();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setCategoryCode(category);
        product.setAvailable(true);
        product.setPriceMedium(10D);
        return product;
    }
}
