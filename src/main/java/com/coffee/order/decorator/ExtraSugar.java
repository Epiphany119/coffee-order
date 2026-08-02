package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器：加糖（适合茶饮调整甜度）
 */
public class ExtraSugar extends CondimentDecorator {
    public ExtraSugar(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 1;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 加糖";
    }
}
