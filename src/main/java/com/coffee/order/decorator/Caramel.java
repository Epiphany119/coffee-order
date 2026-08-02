package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

public class Caramel extends CondimentDecorator {
    public Caramel(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 5;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 焦糖";
    }
}
