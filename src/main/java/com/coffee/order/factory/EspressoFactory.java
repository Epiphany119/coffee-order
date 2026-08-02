package com.coffee.order.factory;

import com.coffee.order.model.Beverage;
import com.coffee.order.factory.beverages.Espresso;

public class EspressoFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new Espresso();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 用高压萃取意式浓缩...");
    }
}
