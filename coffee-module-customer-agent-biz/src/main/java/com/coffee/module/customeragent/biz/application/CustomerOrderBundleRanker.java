package com.coffee.module.customeragent.biz.application;

import com.coffee.module.menu.api.dto.MenuItemDTO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic, bounded combination ranking for customer order recommendations.
 * LLM output is deliberately not an input to this class.
 */
final class CustomerOrderBundleRanker {
    static final int MAX_CANDIDATES_PER_CATEGORY = 10;
    static final int MAX_CANDIDATES_PER_EXPLICIT_TERM = 12;
    static final int MAX_GENERAL_CANDIDATES = 16;
    static final int MAX_SEARCH_STATES = 50_000;
    private static final double BUDGET_EPSILON = 0.0001d;
    private static final double MMR_RELEVANCE_WEIGHT = 0.80d;
    private static final double MMR_DIVERSITY_WEIGHT = 0.20d;

    List<RankedBundle> rank(List<Candidate> candidates,
                            CustomerOrderIntentParser.Intent intent,
                            int optionLimit) {
        if (candidates == null || candidates.isEmpty() || intent == null || optionLimit <= 0) return List.of();

        List<RankedBundle> ranked = rankAll(candidates, intent);
        return ranked.isEmpty() ? List.of() : diversify(ranked, optionLimit);
    }

    /** All feasible combinations in deterministic relevance order, within the bounded search budget. */
    List<RankedBundle> rankAll(List<Candidate> candidates,
                               CustomerOrderIntentParser.Intent intent) {
        if (candidates == null || candidates.isEmpty() || intent == null) return List.of();

        int targetCount = intent.itemCount();
        if (targetCount <= 0) return List.of();

        Comparator<Candidate> candidateOrder = candidateOrder(intent.pricePreference());
        List<Candidate> pool = buildCandidatePool(candidates, intent, candidateOrder);
        if (pool.isEmpty()) return List.of();

        Search search = new Search(pool, intent, targetCount);
        search.visit(0, new ArrayList<>(), 0d);
        if (search.bundles.isEmpty()) return List.of();

        search.bundles.sort(bundleOrder(intent.pricePreference()));
        return List.copyOf(search.bundles);
    }

    private List<Candidate> buildCandidatePool(List<Candidate> input,
                                               CustomerOrderIntentParser.Intent intent,
                                               Comparator<Candidate> order) {
        List<Candidate> ordered = input.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> isEligible(candidate.product(), intent))
                .filter(candidate -> candidate.rankPosition() >= 0)
                .filter(candidate -> candidate.maxQuantity() > 0)
                .filter(candidate -> Double.isFinite(candidate.price()) && candidate.price() >= 0d)
                .sorted(order)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(candidate -> safe(candidate.product().getCode()), candidate -> candidate,
                                (first, ignored) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));

        LinkedHashMap<String, Candidate> selected = new LinkedHashMap<>();
        for (String term : intent.explicitProductNames()) {
            ordered.stream()
                    .filter(candidate -> matchesExplicit(candidate.product(), term))
                    .limit(MAX_CANDIDATES_PER_EXPLICIT_TERM)
                    .forEach(candidate -> selected.putIfAbsent(candidate.product().getCode(), candidate));
        }
        for (String category : intent.requiredCategories()) {
            ordered.stream()
                    .filter(candidate -> matchesCategory(candidate.product(), category))
                    .limit(MAX_CANDIDATES_PER_CATEGORY)
                    .forEach(candidate -> selected.putIfAbsent(candidate.product().getCode(), candidate));
        }
        ordered.stream()
                .limit(MAX_GENERAL_CANDIDATES)
                .forEach(candidate -> selected.putIfAbsent(candidate.product().getCode(), candidate));

        return selected.values().stream().sorted(order).toList();
    }

    private boolean isEligible(MenuItemDTO product, CustomerOrderIntentParser.Intent intent) {
        if (product == null || safe(product.getCode()).isBlank() || safe(product.getName()).isBlank()) return false;
        if (CustomerOrderIntentCatalog.matchesExcludedProductOrCategory(
                product, intent.excludedProducts(), intent.excludedCategories())) return false;
        if (!CustomerOrderIntentCatalog.matchesTemperature(product, intent.temperature())) return false;
        return !CustomerOrderIntentCatalog.isFillerOnly(product)
                || intent.explicitProductNames().stream().anyMatch(term -> matchesExplicit(product, term));
    }

    private Comparator<Candidate> candidateOrder(CustomerOrderIntentParser.PricePreference preference) {
        Comparator<Candidate> stable = Comparator
                .comparing((Candidate candidate) -> safe(candidate.product().getCode()))
                .thenComparing(candidate -> candidate.product().getId(), Comparator.nullsLast(Long::compareTo))
                .thenComparing(candidate -> safe(candidate.product().getName()));
        if (preference != null && preference != CustomerOrderIntentParser.PricePreference.NONE) {
            return Comparator.comparingInt(Candidate::rankPosition)
                    .thenComparing(Comparator.comparingInt(Candidate::score).reversed())
                    .thenComparingDouble(Candidate::price)
                    .thenComparing(stable);
        }
        return Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingInt(Candidate::rankPosition)
                .thenComparingDouble(Candidate::price)
                .thenComparing(stable);
    }

    private Comparator<RankedBundle> bundleOrder(CustomerOrderIntentParser.PricePreference preference) {
        Comparator<RankedBundle> stable = Comparator.comparing(RankedBundle::signature);
        if (preference != null && preference != CustomerOrderIntentParser.PricePreference.NONE) {
            return Comparator.comparingInt(RankedBundle::preferenceRankSum)
                    .thenComparing(Comparator.comparingInt(RankedBundle::score).reversed())
                    .thenComparingDouble(RankedBundle::totalPrice)
                    .thenComparing(stable);
        }
        return Comparator.comparingInt(RankedBundle::score).reversed()
                .thenComparingDouble(RankedBundle::totalPrice)
                .thenComparing(stable);
    }

    private List<RankedBundle> diversify(List<RankedBundle> ranked, int optionLimit) {
        List<RankedBundle> remaining = new ArrayList<>(ranked);
        List<RankedBundle> selected = new ArrayList<>();
        Map<String, Integer> rankPositions = new HashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            rankPositions.putIfAbsent(ranked.get(index).signature(), index);
        }
        selected.add(remaining.remove(0));

        while (!remaining.isEmpty() && selected.size() < optionLimit) {
            int bestIndex = 0;
            double bestMmr = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < remaining.size(); index++) {
                RankedBundle candidate = remaining.get(index);
                double relevance = ranked.size() <= 1 ? 1d
                        : 1d - rankPositions.getOrDefault(candidate.signature(), ranked.size() - 1)
                        / (double) (ranked.size() - 1);
                double similarity = selected.stream()
                        .mapToDouble(chosen -> multisetJaccard(candidate.products(), chosen.products()))
                        .max().orElse(0d);
                double mmr = MMR_RELEVANCE_WEIGHT * relevance - MMR_DIVERSITY_WEIGHT * similarity;
                if (mmr > bestMmr + 1e-12) {
                    bestMmr = mmr;
                    bestIndex = index;
                }
            }
            selected.add(remaining.remove(bestIndex));
        }
        return List.copyOf(selected);
    }

    private double multisetJaccard(List<MenuItemDTO> left, List<MenuItemDTO> right) {
        Map<String, Integer> leftCounts = counts(left);
        Map<String, Integer> rightCounts = counts(right);
        Set<String> codes = new HashSet<>(leftCounts.keySet());
        codes.addAll(rightCounts.keySet());
        int intersection = 0;
        int union = 0;
        for (String code : codes) {
            int leftCount = leftCounts.getOrDefault(code, 0);
            int rightCount = rightCounts.getOrDefault(code, 0);
            intersection += Math.min(leftCount, rightCount);
            union += Math.max(leftCount, rightCount);
        }
        return union == 0 ? 0d : intersection / (double) union;
    }

    private Map<String, Integer> counts(List<MenuItemDTO> products) {
        Map<String, Integer> counts = new HashMap<>();
        for (MenuItemDTO product : products) counts.merge(safe(product.getCode()), 1, Integer::sum);
        return counts;
    }

    private boolean matchesCategory(MenuItemDTO product, String category) {
        return CustomerOrderIntentCatalog.matchesCategory(product, category);
    }

    private boolean matchesExplicit(MenuItemDTO product, String term) {
        return CustomerOrderIntentCatalog.matchesExplicitProduct(product, term);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    record Candidate(MenuItemDTO product, int score, int rankPosition, double price, int maxQuantity) {
        Candidate(MenuItemDTO product, int score, int rankPosition, double price) {
            this(product, score, rankPosition, price, Integer.MAX_VALUE);
        }

        Candidate {
            maxQuantity = Math.max(0, maxQuantity);
        }
    }

    record RankedBundle(List<MenuItemDTO> products, int score, int preferenceRankSum,
                        double totalPrice, String signature) {
        RankedBundle {
            products = List.copyOf(products);
        }
    }

    private final class Search {
        private final List<Candidate> pool;
        private final CustomerOrderIntentParser.Intent intent;
        private final int targetCount;
        private final List<RankedBundle> bundles = new ArrayList<>();
        private int states;

        private Search(List<Candidate> pool, CustomerOrderIntentParser.Intent intent, int targetCount) {
            this.pool = pool;
            this.intent = intent;
            this.targetCount = targetCount;
        }

        private void visit(int start, List<Candidate> current, double totalPrice) {
            if (++states > MAX_SEARCH_STATES) return;

            int remainingSlots = targetCount - current.size();
            if (remainingSlots == 0) {
                addIfFeasible(current, totalPrice);
                return;
            }
            if (start >= pool.size() || remainingSlots < missingRequirementLowerBound(current)
                    || !allRequirementsStillReachable(start, current)) return;

            for (int index = start; index < pool.size(); index++) {
                if (states >= MAX_SEARCH_STATES) return;
                Candidate candidate = pool.get(index);
                long selectedQuantity = current.stream()
                        .filter(existing -> safe(existing.product().getCode())
                                .equals(safe(candidate.product().getCode())))
                        .count();
                if (selectedQuantity >= candidate.maxQuantity()) continue;
                double nextTotal = totalPrice + candidate.price();
                if (intent.budget() != null && nextTotal > intent.budget() + BUDGET_EPSILON) continue;

                current.add(candidate);
                // Same product may satisfy an explicit quantity request (e.g. two cups of one drink).
                visit(index, current, nextTotal);
                current.remove(current.size() - 1);
            }
        }

        private int missingRequirementLowerBound(List<Candidate> current) {
            boolean missingCategory = false;
            for (String category : intent.requiredCategories()) {
                if (current.stream().noneMatch(candidate -> matchesCategory(candidate.product(), category))) {
                    missingCategory = true;
                    break;
                }
            }
            boolean missingExplicitTerm = false;
            for (String term : intent.explicitProductNames()) {
                if (current.stream().noneMatch(candidate -> matchesExplicit(candidate.product(), term)))
                    missingExplicitTerm = true;
            }
            // Category aliases can overlap (e.g. one coffee product satisfies "drink" and
            // "coffee"), and one product can satisfy both a category and a named term.
            // Keep this bound conservative so pruning can never discard a feasible bundle.
            return Math.max(missingCategory ? 1 : 0, missingExplicitTerm ? 1 : 0);
        }

        private boolean allRequirementsStillReachable(int start, List<Candidate> current) {
            for (String category : intent.requiredCategories()) {
                if (current.stream().noneMatch(candidate -> matchesCategory(candidate.product(), category))
                        && pool.subList(start, pool.size()).stream()
                        .noneMatch(candidate -> matchesCategory(candidate.product(), category))) return false;
            }
            for (String term : intent.explicitProductNames()) {
                if (current.stream().noneMatch(candidate -> matchesExplicit(candidate.product(), term))
                        && pool.subList(start, pool.size()).stream()
                        .noneMatch(candidate -> matchesExplicit(candidate.product(), term))) return false;
            }
            return true;
        }

        private void addIfFeasible(List<Candidate> current, double totalPrice) {
            for (String category : intent.requiredCategories()) {
                if (current.stream().noneMatch(candidate -> matchesCategory(candidate.product(), category))) return;
            }
            for (String term : intent.explicitProductNames()) {
                if (current.stream().noneMatch(candidate -> matchesExplicit(candidate.product(), term))) return;
            }
            if (intent.budget() != null && totalPrice > intent.budget() + BUDGET_EPSILON) return;

            int score = current.stream().mapToInt(Candidate::score).sum();
            int preferenceRankSum = current.stream().mapToInt(Candidate::rankPosition).sum();
            List<MenuItemDTO> products = current.stream().map(Candidate::product).toList();
            String signature = products.stream().map(product -> safe(product.getCode()))
                    .sorted().collect(Collectors.joining("|"));
            bundles.add(new RankedBundle(products, score, preferenceRankSum, totalPrice, signature));
        }
    }
}
