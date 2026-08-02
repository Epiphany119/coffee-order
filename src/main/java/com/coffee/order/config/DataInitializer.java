package com.coffee.order.config;

import com.coffee.order.entity.Category;
import com.coffee.order.entity.Product;
import com.coffee.order.repository.CategoryRepository;
import com.coffee.order.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;

    public DataInitializer(CategoryRepository categoryRepo, ProductRepository productRepo) {
        this.categoryRepo = categoryRepo;
        this.productRepo = productRepo;
    }

    @Override
    public void run(String... args) {
        initCategories();
        initProducts();
        System.out.println("=== 数据初始化完成: " + categoryRepo.count() + " 个分类, "
                + productRepo.count() + " 个商品 ===");
    }

    private void initCategories() {
        saveIfNotExists("coffee", () -> new Category("coffee", "咖啡", "☕", 1));
        saveIfNotExists("tea", () -> new Category("tea", "茶饮", "🍵", 2));
        saveIfNotExists("dessert", () -> new Category("dessert", "甜点", "🍰", 3));
        saveIfNotExists("food", () -> new Category("food", "轻食", "🥪", 4));
        saveIfNotExists("ice", () -> new Category("ice", "冰沙", "🧊", 5));
    }

    private void saveIfNotExists(String code, java.util.function.Supplier<Category> saver) {
        if (categoryRepo.findByCode(code).isEmpty()) {
            categoryRepo.save(saver.get());
        }
    }

    private void initProducts() {
        // 咖啡 7
        saveProductIfNotExists("espresso", () ->
            new Product("coffee", "espresso", "意式浓缩", 18,
                "高压萃取，浓郁醇厚",
                "https://images.unsplash.com/photo-1510707577719-ae7c14805e3a?w=300&h=300&fit=crop",
                "HOT"));
        saveProductIfNotExists("americano", () ->
            new Product("coffee", "americano", "美式咖啡", 20,
                "浓缩加水，清爽经典",
                "https://images.unsplash.com/photo-1459755486867-b55449bb39ff?w=300&h=300&fit=crop",
                "BOTH"));
        saveProductIfNotExists("latte", () ->
            new Product("coffee", "latte", "经典拿铁", 24,
                "丝滑奶泡搭配浓缩咖啡，绵密香浓",
                "https://images.unsplash.com/photo-1561882468-9110e03e0f78?w=300&h=300&fit=crop",
                "BOTH"));
        saveProductIfNotExists("cappuccino", () ->
            new Product("coffee", "cappuccino", "卡布奇诺", 24,
                "浓厚奶泡与咖啡的经典比例，口感丰富",
                "https://images.unsplash.com/photo-1572442388796-11668a67e53d?w=300&h=300&fit=crop",
                "HOT"));
        saveProductIfNotExists("mocha", () ->
            new Product("coffee", "mocha", "摩卡咖啡", 28,
                "巧克力与咖啡的甜蜜邂逅，丝滑醇香",
                "https://images.unsplash.com/photo-1578314675249-a6910f80cc4e?w=300&h=300&fit=crop",
                "BOTH"));
        saveProductIfNotExists("flat_white", () ->
            new Product("coffee", "flat_white", "澳白咖啡", 26,
                "澳洲风味，细腻奶泡配双份浓缩",
                "https://images.unsplash.com/photo-1521302080334-4bebac2763a6?w=300&h=300&fit=crop",
                "HOT"));
        saveProductIfNotExists("caramel_macchiato", () ->
            new Product("coffee", "caramel_macchiato", "焦糖玛奇朵", 30,
                "焦糖糖浆与香草的层次之美",
                "https://images.unsplash.com/photo-1485808191679-5f86510681a2?w=300&h=300&fit=crop",
                "BOTH"));

        // 茶饮 7
        saveProductIfNotExists("earl_grey", () ->
            new Product("tea", "earl_grey", "伯爵红茶", 16,
                "经典英式红茶，佛手柑芳香，回味悠长",
                "https://images.unsplash.com/photo-1597318181409-cf64d0b5d8a2?w=300&h=300&fit=crop",
                "BOTH"));
        saveProductIfNotExists("jasmine_tea", () ->
            new Product("tea", "jasmine_tea", "茉莉花茶", 16,
                "清新茉莉花香，甘醇爽口，午后一杯正好",
                "https://images.unsplash.com/photo-1564890369478-c89ca6d9cde9?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("lemon_tea", () ->
            new Product("tea", "lemon_tea", "手打柠檬茶", 18,
                "新鲜柠檬搭配红茶底，酸甜解腻，清爽怡人",
                "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("peach_oolong", () ->
            new Product("tea", "peach_oolong", "蜜桃乌龙", 20,
                "水蜜桃与乌龙茶的清甜组合，清香扑鼻",
                "https://images.unsplash.com/photo-1558857563-b371033873b8?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("yangzhiganlu", () ->
            new Product("tea", "yangzhiganlu", "杨枝甘露", 22,
                "芒果西柚与椰浆的港式经典，丝滑甜蜜",
                "https://images.unsplash.com/photo-1546173159-315724a31696?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("pearl_milk_tea", () ->
            new Product("tea", "pearl_milk_tea", "珍珠奶茶", 25,
                "现煮珍珠撞上香浓奶茶",
                "https://images.unsplash.com/photo-1525385133512-05f88b71c52b?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("dirty_milk_tea", () ->
            new Product("tea", "dirty_milk_tea", "脏脏茶", 28,
                "黑糖挂壁，鲜奶邂逅茶汤",
                "https://images.unsplash.com/photo-1571934811356-5cc061b6821f?w=300&h=300&fit=crop",
                "COLD"));

        // 甜点 7
        saveProductIfNotExists("croissant", () ->
            new Product("dessert", "croissant", "法式可颂", 15,
                "法式酥脆可颂，层层酥皮，黄油香气四溢",
                "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("cheesecake", () ->
            new Product("dessert", "cheesecake", "芝士蛋糕", 28,
                "细腻柔滑的纽约风味芝士蛋糕，奶香浓郁",
                "https://images.unsplash.com/photo-1565958011703-44f9829ba187?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("tiramisu", () ->
            new Product("dessert", "tiramisu", "提拉米苏", 32,
                "意式经典，马斯卡彭与可可的交融，入口即化",
                "https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("cookies", () ->
            new Product("dessert", "cookies", "手工曲奇", 12,
                "手工烘焙黄油曲奇，香酥可口，佐茶必备",
                "https://images.unsplash.com/photo-1499636136210-6f4ee915583e?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("macarons", () ->
            new Product("dessert", "macarons", "法式马卡龙", 18,
                "法式精致小甜点，外酥内软，色彩缤纷",
                "https://images.unsplash.com/photo-1569864358642-9d1684040f43?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("brownie", () ->
            new Product("dessert", "brownie", "巧克力布朗尼", 22,
                "外脆内软，浓郁巧克力",
                "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("lava_cake", () ->
            new Product("dessert", "lava_cake", "巧克力熔岩蛋糕", 35,
                "切开流心的浓郁巧克力蛋糕，甜蜜暴击",
                "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=300&h=300&fit=crop",
                "HOT"));

        // 甜点 蛋糕 8
        saveProductIfNotExists("strawberry_cake", () ->
            new Product("dessert", "strawberry_cake", "草莓奶油蛋糕", 38,
                "新鲜草莓搭配轻盈奶油，可定制祝福语",
                "https://images.unsplash.com/photo-1565958011703-44f9829ba187?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("matcha_layer", () ->
            new Product("dessert", "matcha_layer", "抹茶千层蛋糕", 36,
                "日式宇治抹茶与薄饼层完美叠加，清香不腻",
                "https://images.unsplash.com/photo-1562440499-64c9a111f713?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("blackforest", () ->
            new Product("dessert", "blackforest", "黑森林蛋糕", 42,
                "德国经典，樱桃巧克力与鲜奶油的交织",
                "https://images.unsplash.com/photo-1571115177098-24ec42ed204d?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("montblanc", () ->
            new Product("dessert", "montblanc", "蒙布朗", 45,
                "法式栗子蛋糕，秋冬限定，绵密香甜",
                "https://images.unsplash.com/photo-1607920592519-bab8c43a0f2b?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("roll_cake", () ->
            new Product("dessert", "roll_cake", "瑞士卷8寸", 32,
                "柔软海绵蛋糕卷入鲜奶油，四种口味可选",
                "https://images.unsplash.com/photo-1587314168485-3236d6710814?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("panna_cotta", () ->
            new Product("dessert", "panna_cotta", "意式奶冻", 28,
                "丝滑奶油与香草的交融，入口即化",
                "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("cheese_tart", () ->
            new Product("dessert", "cheese_tart", "半熟芝士挞", 22,
                "外酥内嫩的半熟芝士挞，浓郁拉丝",
                "https://images.unsplash.com/photo-1509365465985-25d11c17e812?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("basque_burnt", () ->
            new Product("dessert", "basque_burnt", "巴斯克烧焦芝士", 30,
                "西班牙巴斯克风格，外焦内软，芝士浓郁",
                "https://images.unsplash.com/photo-1519915028121-7d3463d20b13?w=300&h=300&fit=crop",
                "COLD"));

        // 轻食 6
        saveProductIfNotExists("club_sandwich", () ->
            new Product("food", "club_sandwich", "俱乐部三明治", 28,
                "鸡肉培根生菜三层",
                "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("egg_tart", () ->
            new Product("food", "egg_tart", "港式蛋挞6个装", 22,
                "酥脆外皮包裹嫩滑蛋奶馅，经典港味",
                "https://images.unsplash.com/photo-1509365465985-25d11c17e812?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("fruit_tart", () ->
            new Product("food", "fruit_tart", "鲜果塔", 26,
                "新鲜当季水果搭配酥塔，清爽不腻",
                "https://images.unsplash.com/photo-1464305795204-6f5bbfc7fb81?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("yogurt_parfait", () ->
            new Product("food", "yogurt_parfait", "酸奶水果杯", 24,
                "希腊酸奶搭配谷物与时令鲜果",
                "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("avocado_toast", () ->
            new Product("food", "avocado_toast", "牛油果吐司", 30,
                "香烤吐司上铺满牛油果泥，营养轻食",
                "https://images.unsplash.com/photo-1541519227354-08fa5d50c44d?w=300&h=300&fit=crop",
                "ROOM"));
        saveProductIfNotExists("chicken_wrap", () ->
            new Product("food", "chicken_wrap", "鸡肉卷饼", 26,
                "香煎鸡胸肉与蔬菜卷入薄饼，轻盈健康",
                "https://images.unsplash.com/photo-1626700051175-6818013e1d4f?w=300&h=300&fit=crop",
                "ROOM"));

        // 冰沙 6
        saveProductIfNotExists("mango_smoothie", () ->
            new Product("ice", "mango_smoothie", "芒果冰沙", 28,
                "热带芒果，绵密香甜",
                "https://images.unsplash.com/photo-1623065422902-30a2d299bbe4?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("strawberry_smoothie", () ->
            new Product("ice", "strawberry_smoothie", "草莓冰沙", 26,
                "新鲜草莓，酸甜可口",
                "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("matcha_smoothie", () ->
            new Product("ice", "matcha_smoothie", "抹茶冰沙", 30,
                "日式宇治抹茶，清爽回甘",
                "https://images.unsplash.com/photo-1546039907-7fa05f864c02?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("watermelon_smoothie", () ->
            new Product("ice", "watermelon_smoothie", "西瓜冰沙", 24,
                "盛夏西瓜，消暑解渴",
                "https://images.unsplash.com/photo-1528821128474-27f963b062bf?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("blueberry_smoothie", () ->
            new Product("ice", "blueberry_smoothie", "蓝莓冰沙", 28,
                "北美蓝莓，花青素满满",
                "https://images.unsplash.com/photo-1502741224143-90386d7f8c82?w=300&h=300&fit=crop",
                "COLD"));
        saveProductIfNotExists("coconut_smoothie", () ->
            new Product("ice", "coconut_smoothie", "椰汁冰沙", 26,
                "清爽椰汁搭配细腻冰沙，热带风情",
                "https://images.unsplash.com/photo-1536657464919-892534f60d6e?w=300&h=300&fit=crop",
                "COLD"));
    }

    private void saveProductIfNotExists(String code, java.util.function.Supplier<Product> factory) {
        if (productRepo.findByCode(code).isEmpty()) {
            productRepo.save(factory.get());
        }
    }
}
