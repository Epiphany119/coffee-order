package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器：椰果
 */
public class CoconutJelly extends CondimentDecorator {
    public CoconutJelly(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 3;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 椰果";
    }
}
