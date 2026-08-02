package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

public class Mocha extends CondimentDecorator {
    public Mocha(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 6;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 摩卡";
    }
}
