package com.coffee.order.factory;

import com.coffee.order.model.Beverage;

/**
 * 模板方法模式：饮品制作模板
 * 定义了制作饮品的标准流程，子类实现具体步骤
 */
public abstract class BeverageFactory {

    // 模板方法：定义制作流程
    public final Beverage prepareBeverage() {
        Beverage beverage = createBeverage();
        brew(beverage);
        addExtras(beverage);
        System.out.println("[制作完成] " + beverage.getName() + " - ¥" + beverage.cost());
        return beverage;
    }

    // 工厂方法：由子类决定创建哪种饮品
    protected abstract Beverage createBeverage();

    protected void brew(Beverage beverage) {
        System.out.println("  > 正在冲泡...");
    }

    protected void addExtras(Beverage beverage) {
        System.out.println("  > 添加配料...");
    }
}
