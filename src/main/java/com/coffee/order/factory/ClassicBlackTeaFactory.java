package com.coffee.order.factory;

import com.coffee.order.factory.beverages.ClassicBlackTea;
import com.coffee.order.model.Beverage;

public class ClassicBlackTeaFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new ClassicBlackTea();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 沸水冲泡经典红茶...");
    }
}
