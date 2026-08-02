package com.coffee.order.factory;

import com.coffee.order.model.Beverage;
import com.coffee.order.factory.beverages.Cappuccino;

public class CappuccinoFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new Cappuccino();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 萃取浓缩咖啡...");
    }

    @Override
    protected void addExtras(Beverage beverage) {
        System.out.println("  > 加入大量厚奶泡...");
    }
}
