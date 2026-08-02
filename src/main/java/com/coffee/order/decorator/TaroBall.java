package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器：芋圆（珍珠/芋圆，用于茶饮）
 */
public class TaroBall extends CondimentDecorator {
    public TaroBall(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 5;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 芋圆";
    }
}
