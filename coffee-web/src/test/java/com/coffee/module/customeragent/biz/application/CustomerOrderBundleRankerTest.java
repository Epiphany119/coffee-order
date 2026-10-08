package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerOrderBundleRankerTest {
    private final CustomerOrderBundleRanker ranker = new CustomerOrderBundleRanker();
    private final CustomerOrderIntentParser parser = new CustomerOrderIntentParser();

    @Test
    void multiCategoryBundleCoversEveryRequiredCategoryAndFiltersOverBudgetCombos() {
        CustomerOrderIntentParser.Intent intent = parser.parse("蛋糕加咖啡，预算40");
        MenuItemDTO expensiveCake = product(1L, "cake-expensive", "草莓奶油蛋糕", "dessert");
        MenuItemDTO bestFeasibleCake = product(2L, "cake-best", "芝士蛋糕", "dessert");
        MenuItemDTO cheaperCake = product(3L, "cake-cheap", "抹茶千层蛋糕", "dessert");
        MenuItemDTO coffee = product(4L, "coffee", "美式咖啡", "coffee");

        List<CustomerOrderBundleRanker.RankedBundle> bundles = ranker.rank(List.of(
                candidate(expensiveCake, 200, 0, 30),
                candidate(bestFeasibleCake, 120, 1, 20),
                candidate(cheaperCake, 90, 2, 10),
                candidate(coffee, 20, 3, 18)), intent, 3);

        assertFalse(bundles.isEmpty());
        assertEquals(2, bundles.get(0).products().size());
        // The parser treats the explicitly named cake group and coffee as required categories.
        assertTrue(bundles.get(0).products().stream().anyMatch(p -> "dessert".equals(p.getCategoryCode())));
        assertTrue(bundles.get(0).products().stream().anyMatch(p -> "coffee".equals(p.getCategoryCode())));
        assertTrue(bundles.stream().allMatch(bundle -> bundle.totalPrice() <= 40.0001));
    }

    @Test
    void specificNamedProductCannotBeReplacedByAnotherProductInTheSameCategory() {
        CustomerOrderIntentParser.Intent intent = parser.parse("芝士蛋糕加咖啡，预算40");
        MenuItemDTO cheesecake = product(1L, "cheesecake", "芝士蛋糕", "dessert");
        MenuItemDTO blackForest = product(2L, "blackforest", "黑森林蛋糕", "dessert");
        MenuItemDTO coffee = product(3L, "coffee", "美式咖啡", "coffee");

        List<CustomerOrderBundleRanker.RankedBundle> bundles = ranker.rank(List.of(
                candidate(blackForest, 200, 0, 18),
                candidate(cheesecake, 10, 1, 18),
                candidate(coffee, 30, 2, 15)), intent, 3);

        assertFalse(bundles.isEmpty());
        assertTrue(bundles.stream().allMatch(bundle -> bundle.products().stream()
                .anyMatch(product -> "cheesecake".equals(product.getCode()))));
    }

    @Test
    void missingRequiredCategoryReturnsNoBundleInsteadOfSubstitutingAnotherCategory() {
        CustomerOrderIntentParser.Intent intent = parser.parse("蛋糕加咖啡");
        MenuItemDTO cakeA = product(1L, "cake-a", "芝士蛋糕", "dessert");
        MenuItemDTO cakeB = product(2L, "cake-b", "黑森林蛋糕", "dessert");

        assertTrue(ranker.rank(List.of(candidate(cakeA, 20, 0, 10), candidate(cakeB, 10, 1, 10)),
                intent, 3).isEmpty());
    }

    @Test
    void sameProductCanSatisfyAnExplicitQuantityAndIsRepresentedAsMultipleUnits() {
        CustomerOrderIntentParser.Intent intent = parser.parse("两杯咖啡");
        MenuItemDTO latte = product(1L, "latte", "拿铁", "coffee");

        List<CustomerOrderBundleRanker.RankedBundle> bundles = ranker.rank(
                List.of(candidate(latte, 30, 0, 16)), intent, 1);

        assertEquals(1, bundles.size());
        assertEquals(List.of("latte", "latte"), bundles.get(0).products().stream()
                .map(MenuItemDTO::getCode).toList());
        assertEquals(32d, bundles.get(0).totalPrice());
    }

    @Test
    void oneCoffeeCanCoverOverlappingDrinkAndCoffeeRequirements() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser.Intent(
                List.of("drink", "coffee"), java.util.Set.of(), java.util.Set.of(),
                CustomerOrderIntentParser.Temperature.ANY, null, false, 1,
                CustomerOrderIntentParser.PricePreference.NONE, "饮品和咖啡", List.of());
        MenuItemDTO coffee = product(1L, "coffee", "美式咖啡", "coffee");

        List<CustomerOrderBundleRanker.RankedBundle> bundles = ranker.rank(
                List.of(candidate(coffee, 20, 0, 18)), intent, 1);

        assertEquals(1, bundles.size());
        assertEquals(List.of("coffee"), bundles.get(0).products().stream()
                .map(MenuItemDTO::getCode).toList());
    }

    @Test
    void inventorySnapshotPreventsSearchFromRepeatingAProductBeyondAvailableStock() {
        CustomerOrderIntentParser.Intent intent = parser.parse("两杯咖啡");
        MenuItemDTO latte = product(1L, "latte", "拿铁", "coffee");

        List<CustomerOrderBundleRanker.RankedBundle> bundles = ranker.rank(List.of(
                new CustomerOrderBundleRanker.Candidate(latte, 30, 0, 16, 1)), intent, 1);

        assertTrue(bundles.isEmpty());
    }

    @Test
    void oneUnitCannotPretendToCoverTwoDisjointRequiredCategories() {
        CustomerOrderIntentParser.Intent intent = new CustomerOrderIntentParser.Intent(
                List.of("dessert", "coffee"), java.util.Set.of(), java.util.Set.of(),
                CustomerOrderIntentParser.Temperature.ANY, null, false, 1,
                CustomerOrderIntentParser.PricePreference.NONE, "蛋糕和咖啡", List.of());
        MenuItemDTO cake = product(1L, "cake", "芝士蛋糕", "dessert");
        MenuItemDTO coffee = product(2L, "coffee", "美式咖啡", "coffee");

        assertTrue(ranker.rank(List.of(candidate(cake, 20, 0, 18), candidate(coffee, 20, 1, 18)),
                intent, 1).isEmpty());
    }

    @Test
    void tiedResultsAreDeterministicAndAlternativeBundlesAreUnique() {
        CustomerOrderIntentParser.Intent intent = parser.parse("蛋糕加咖啡");
        MenuItemDTO cakeA = product(1L, "cake-a", "芝士蛋糕", "dessert");
        MenuItemDTO cakeB = product(2L, "cake-b", "黑森林蛋糕", "dessert");
        MenuItemDTO coffeeA = product(3L, "coffee-a", "美式咖啡", "coffee");
        MenuItemDTO coffeeB = product(4L, "coffee-b", "拿铁", "coffee");
        List<CustomerOrderBundleRanker.Candidate> candidates = List.of(
                candidate(cakeA, 50, 0, 10), candidate(cakeB, 50, 0, 10),
                candidate(coffeeA, 50, 0, 10), candidate(coffeeB, 50, 0, 10));

        List<String> firstRun = signatures(ranker.rank(candidates, intent, 3));
        List<CustomerOrderBundleRanker.Candidate> reversed = new ArrayList<>(candidates);
        java.util.Collections.reverse(reversed);
        List<String> secondRun = signatures(ranker.rank(reversed, intent, 3));

        assertEquals(firstRun, secondRun);
        assertEquals(firstRun.size(), firstRun.stream().distinct().count());
        assertEquals(3, firstRun.size());
    }

    private List<String> signatures(List<CustomerOrderBundleRanker.RankedBundle> bundles) {
        return bundles.stream().map(CustomerOrderBundleRanker.RankedBundle::signature).toList();
    }

    private CustomerOrderBundleRanker.Candidate candidate(MenuItemDTO product,
                                                            int score, int rank, double price) {
        return new CustomerOrderBundleRanker.Candidate(product, score, rank, price);
    }

    private MenuItemDTO product(Long id, String code, String name, String category) {
        MenuItemDTO product = new MenuItemDTO();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setCategoryCode(category);
        product.setAvailable(true);
        return product;
    }
}
