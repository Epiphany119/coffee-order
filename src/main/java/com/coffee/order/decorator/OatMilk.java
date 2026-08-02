package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器：燕麦奶（植物基替换）
 */
public class OatMilk extends CondimentDecorator {
    public OatMilk(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 4;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 燕麦奶";
    }
}
