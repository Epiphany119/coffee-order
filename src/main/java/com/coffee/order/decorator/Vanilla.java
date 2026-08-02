package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

public class Vanilla extends CondimentDecorator {
    public Vanilla(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 3;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 香草";
    }
}
