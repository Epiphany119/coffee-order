package com.coffee.order.factory;

import com.coffee.order.factory.beverages.DirtyMilkTea;
import com.coffee.order.model.Beverage;

public class DirtyMilkTeaFactory extends BeverageFactory {
    @Override
    protected Beverage createBeverage() {
        return new DirtyMilkTea();
    }

    @Override
    protected void brew(Beverage beverage) {
        System.out.println("  > 现萃茶底，挂壁黑糖，加入鲜奶...");
    }
}
