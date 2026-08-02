package com.coffee.order.factory;

import com.coffee.order.model.Beverage;
import com.coffee.order.factory.beverages.Americano;

public class AmericanoFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new Americano();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 萃取浓缩后加热水稀释...");
    }
}
