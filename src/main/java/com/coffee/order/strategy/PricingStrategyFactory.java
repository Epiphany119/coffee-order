package com.coffee.order.strategy;

import java.util.HashMap;
import java.util.Map;

public class PricingStrategyFactory {
    private static final Map<String, PricingStrategy> STRATEGIES = new HashMap<>();

    static {
        STRATEGIES.put("regular", new RegularPricingStrategy());
        STRATEGIES.put("vip", new VipPricingStrategy());
        STRATEGIES.put("svip", new SvipPricingStrategy());
    }

    public static PricingStrategy getStrategy(String type) {
        PricingStrategy strategy = STRATEGIES.get(type);
        return strategy != null ? strategy : new RegularPricingStrategy();
    }
}
