package com.coffee.order.factory;

import com.coffee.order.model.Beverage;
import com.coffee.order.factory.beverages.Latte;

public class LatteFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new Latte();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 萃取浓缩咖啡并加热牛奶...");
    }

    @Override
    protected void addExtras(Beverage beverage) {
        System.out.println("  > 打奶泡并拉花...");
    }
}
