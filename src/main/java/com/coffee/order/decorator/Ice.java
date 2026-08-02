package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

public class Ice extends CondimentDecorator {
    public Ice(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 2;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 冰块";
    }
}
