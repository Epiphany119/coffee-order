package com.coffee.order.factory.beverages;

import com.coffee.order.model.Beverage;
import com.coffee.order.model.BevSize;

/**
 * 所有饮品的抽象基类
 * 统一管理基础价格，支持按规格加价
 */
public abstract class AbstractBeverage extends Beverage {

    protected double basePrice = 0;

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getBasePrice() {
        return basePrice;
    }

    @Override
    public double cost() {
        return basePrice + size.getExtraPrice();
    }

    @Override
    public String getName() {
        return size.getLabel() + " " + name;
    }

    @Override
    public BevSize getSize() {
        return size;
    }

    @Override
    public void setSize(BevSize size) {
        this.size = size;
    }
}
