package com.coffee.order.factory;

import com.coffee.order.decorator.*;
import com.coffee.order.model.Beverage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产品工厂注册表：把商品 code 映射到具体的 BeverageFactory
 * 这是工厂模式的一个应用 —— 工厂的工厂
 *
 * 注意：每个商品有自己的工厂；甜点和轻食是非饮品，用 NoneBeverage 包装
 */
@Component
public class ProductFactoryRegistry {

    private final Map<String, BeverageFactory> factoryMap = Map.ofEntries(
            Map.entry("espresso", new EspressoFactory()),
            Map.entry("americano", new AmericanoFactory()),
            Map.entry("latte", new LatteFactory()),
            Map.entry("cappuccino", new CappuccinoFactory()),
            Map.entry("jasmine", new JasmineGreenTeaFactory()),
            Map.entry("peach_oolong", new PeachOolongTeaFactory()),
            Map.entry("black_tea", new ClassicBlackTeaFactory()),
            Map.entry("pearl_milk_tea", new PearlMilkTeaFactory()),
            Map.entry("dirty_milk_tea", new DirtyMilkTeaFactory()),
            Map.entry("matcha_latte", new MatchaLatteFactory())
    );

    /**
     * 商品 code -> 允许的装饰器 code 集合
     * 咖啡和茶饮：通用配料
     * 甜点和轻食：不支持装饰器
     * 冰沙：可加芋圆/椰果
     */
    private final Map<String, Set<String>> condimentOptions = Map.ofEntries(
            Map.entry("espresso", Set.of("mocha", "whip", "caramel", "vanilla", "ice", "oat_milk")),
            Map.entry("americano", Set.of("mocha", "whip", "caramel", "vanilla", "ice", "oat_milk")),
            Map.entry("latte", Set.of("mocha", "whip", "caramel", "vanilla", "ice", "oat_milk")),
            Map.entry("cappuccino", Set.of("mocha", "whip", "caramel", "vanilla", "oat_milk")),
            Map.entry("jasmine", Set.of("taro_ball", "coconut", "cheese_foam", "extra_sugar", "ice")),
            Map.entry("peach_oolong", Set.of("taro_ball", "coconut", "cheese_foam", "extra_sugar", "ice")),
            Map.entry("black_tea", Set.of("taro_ball", "coconut", "cheese_foam", "extra_sugar", "ice")),
            Map.entry("pearl_milk_tea", Set.of("taro_ball", "coconut", "cheese_foam", "extra_sugar", "ice")),
            Map.entry("dirty_milk_tea", Set.of("taro_ball", "coconut", "cheese_foam", "extra_sugar")),
            Map.entry("matcha_latte", Set.of("cheese_foam", "taro_ball", "oat_milk")),
            Map.entry("mango_smoothie", Set.of("taro_ball", "coconut")),
            Map.entry("strawberry_smoothie", Set.of("taro_ball", "coconut")),
            Map.entry("matcha_smoothie", Set.of("taro_ball", "coconut", "cheese_foam"))
    );

    /**
     * 根据商品 code 创建饮料对象
     * 甜点和轻食返回 null —— 它们不走 Beverage/装饰器模式
     */
    public Beverage createBeverage(String productCode) {
        BeverageFactory factory = factoryMap.get(productCode);
        if (factory == null) {
            return null;
        }
        return factory.prepareBeverage();
    }

    public boolean hasBeverageFactory(String productCode) {
        return factoryMap.containsKey(productCode);
    }

    /**
     * 根据 productCode + condiments 装配最终饮料
     *
     * @param productCode 商品 code
     * @param condiments  配料列表
     * @param basePrice   商品的真实基础价（由 OrderService 从数据库传入，避免硬编码不一致）
     */
    public Beverage assemble(String productCode, List<String> condiments, double basePrice) {
        Beverage beverage = createBeverage(productCode);
        if (beverage == null) {
            return null;
        }
        if (beverage instanceof com.coffee.order.factory.beverages.AbstractBeverage ab) {
            ab.setBasePrice(basePrice);
        }
        if (condiments == null) return beverage;
        for (String c : condiments) {
            beverage = applyCondiment(beverage, c);
        }
        return beverage;
    }

    /** 兼容旧调用：默认 basePrice=0（不推荐，应使用三参版本） */
    public Beverage assemble(String productCode, List<String> condiments) {
        return assemble(productCode, condiments, 0);
    }

    private Beverage applyCondiment(Beverage beverage, String type) {
        return switch (type) {
            case "mocha" -> new Mocha(beverage);
            case "whip" -> new Whip(beverage);
            case "caramel" -> new Caramel(beverage);
            case "vanilla" -> new Vanilla(beverage);
            case "ice" -> new Ice(beverage);
            case "taro_ball" -> new TaroBall(beverage);
            case "coconut" -> new CoconutJelly(beverage);
            case "cheese_foam" -> new CheeseFoam(beverage);
            case "extra_sugar" -> new ExtraSugar(beverage);
            case "oat_milk" -> new OatMilk(beverage);
            default -> beverage;
        };
    }

    /**
     * 返回商品允许的所有装饰器（前端动态渲染按钮用）
     */
    public Set<String> getAllowedCondiments(String productCode) {
        return condimentOptions.getOrDefault(productCode, Set.of());
    }
}
