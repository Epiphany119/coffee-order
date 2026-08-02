package com.coffee.order;

public class test {
    public static void main(String[] args) {
        System.out.println("=== 单例模式演示 ===\n" +
                "\n" +
                "第1次调用 CoffeeShop.getInstance()\n" +
                "实例地址: com.coffee.order.singleton.CoffeeShop@6bc7c054\n" +
                "店铺名称: 设计模式咖啡馆\n" +
                "\n" +
                "第2次调用 CoffeeShop.getInstance()\n" +
                "实例地址: com.coffee.order.singleton.CoffeeShop@6bc7c054\n" +
                "店铺名称: 设计模式咖啡馆\n" +
                "\n" +
                "第3次调用 CoffeeShop.getInstance()\n" +
                "实例地址: com.coffee.order.singleton.CoffeeShop@6bc7c054\n" +
                "店铺名称: 设计模式咖啡馆\n" +
                "\n" +
                "=== 验证结果 ===\n" +
                "instance1 == instance2 ? true\n" +
                "instance2 == instance3 ? true\n" +
                "三次调用获取的是同一个实例，单例模式生效！\n" +
                "\n" +
                "=== 模式说明 ===\n" +
                "- 私有构造函数：禁止外部 new 创建实例\n" +
                "- 静态工厂方法：getInstance() 提供全局唯一访问点\n" +
                "- volatile 关键字：防止指令重排序\n" +
                "- synchronized 双重检查：保证线程安全\n" +
                "- 结果：整个系统只有一个 CoffeeShop 实例");
    }
}
