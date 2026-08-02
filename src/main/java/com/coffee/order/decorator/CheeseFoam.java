package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器：奶盖（咸/甜芝士奶盖，茶饮咖啡通用）
 */
public class CheeseFoam extends CondimentDecorator {
    public CheeseFoam(Beverage beverage) {
        super(beverage);
    }

    @Override
    public double cost() {
        return beverage.cost() + 6;
    }

    @Override
    public String getName() {
        return beverage.getName() + " + 芝士奶盖";
    }
}
