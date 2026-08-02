package com.coffee.order.factory;

import com.coffee.order.factory.beverages.PeachOolongTea;
import com.coffee.order.model.Beverage;

public class PeachOolongTeaFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new PeachOolongTea();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 高温焖泡蜜桃乌龙，加入蜜桃果酱...");
    }
}
