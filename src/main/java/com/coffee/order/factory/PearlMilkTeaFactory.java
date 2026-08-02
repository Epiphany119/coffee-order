package com.coffee.order.factory;

import com.coffee.order.factory.beverages.PearlMilkTea;
import com.coffee.order.model.Beverage;

public class PearlMilkTeaFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new PearlMilkTea();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 熬煮珍珠，加入红茶与鲜奶...");
    }
}
