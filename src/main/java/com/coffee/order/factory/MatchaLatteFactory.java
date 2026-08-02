package com.coffee.order.factory;

import com.coffee.order.factory.beverages.MatchaLatte;
import com.coffee.order.model.Beverage;

public class MatchaLatteFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new MatchaLatte();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 打发抹茶，加入温热牛奶...");
    }
}
