package com.coffee.order.decorator;

import com.coffee.order.model.Beverage;

/**
 * 装饰器模式：配料装饰器基类
 * 通过组合的方式动态地为饮品添加配料，不修改原有类
 */
public abstract class CondimentDecorator extends Beverage {
    protected Beverage beverage;

    public CondimentDecorator(Beverage beverage) {
        this.beverage = beverage;
    }

    @Override
    public abstract String getName();
}
