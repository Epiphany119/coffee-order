package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuChatClient;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
}
