package com.coffee.order.factory;

import com.coffee.order.factory.beverages.JasmineGreenTea;
import com.coffee.order.model.Beverage;

public class JasmineGreenTeaFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new JasmineGreenTea();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 85度水温冲泡茉莉绿茶...");
    }
}
