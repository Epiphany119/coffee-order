package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

public class Whip extends CondimentDecorator {
    public Whip(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 4;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 奶油";
    }
}
