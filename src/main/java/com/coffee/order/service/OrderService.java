package com.coffee.order.service;

import com.coffee.order.dto.CartItemRequest;
import com.coffee.order.dto.OrderRequest;
import com.coffee.order.dto.OrderResponse;
import com.coffee.order.entity.CoffeeUser;
import com.coffee.order.entity.GuestOrder;
import com.coffee.order.entity.MemberPoints;
import com.coffee.order.entity.Product;
import com.coffee.order.entity.UserOrder;
import com.coffee.order.factory.ProductFactoryRegistry;
import com.coffee.order.model.Beverage;
import com.coffee.order.model.BevSize;
import com.coffee.order.observer.CustomerNotifier;
import com.coffee.order.observer.KitchenDisplay;
import com.coffee.order.observer.OrderPublisher;
import com.coffee.order.repository.*;
import com.coffee.order.state.OrderStateContext;
import com.coffee.order.strategy.PricingStrategy;
import com.coffee.order.strategy.PricingStrategyFactory;
import com.coffee.order.strategy.SpendingDiscountStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OrderService {
    private final OrderPublisher publisher = new OrderPublisher();
    private final ProductRepository productRepo;
    private final CoffeeUserRepository userRepo;
    private final UserOrderRepository userOrderRepo;
    private final GuestOrderRepository guestOrderRepo;
    private final ProductFactoryRegistry factoryRegistry;
    private final MemberPointsRepository memberPointsRepo;

    public OrderService(ProductRepository productRepo,
                        CoffeeUserRepository userRepo,
                        UserOrderRepository userOrderRepo,
                        GuestOrderRepository guestOrderRepo,
                        ProductFactoryRegistry factoryRegistry,
                        MemberPointsRepository memberPointsRepo) {
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.userOrderRepo = userOrderRepo;
        this.guestOrderRepo = guestOrderRepo;
        this.factoryRegistry = factoryRegistry;
        this.memberPointsRepo = memberPointsRepo;

        publisher.subscribe(new CustomerNotifier());
        publisher.subscribe(new KitchenDisplay());
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        List<CartItemRequest> cartItems = request.getItems();
        if (cartItems != null && !cartItems.isEmpty()) {
            return createBatchOrder(request);
        }
        return createSingleOrder(request);
    }

    private OrderResponse createSingleOrder(OrderRequest request) {
        AssembledItem assembled = assembleItem(
                request.getProductCode(),
                request.getSize(),
                request.getCondiments());

        long userId = request.getUserId() == null ? 0L : request.getUserId();
        double original = assembled.unitPrice;
        double discountRate = memberDiscountRate(request.getUserId());
        double afterMember = round2(original * discountRate);
        CouponResult coupon = applyCoupon(request.getCouponCode(), afterMember, request.getUserId() != null);
        OrderResponse resp = persistAndPublish(assembled, userId,
                request.getGuestId() != null ? request.getGuestId() : "anonymous",
                coupon.finalPrice);
        resp.setMemberDiscount(round2(original - afterMember));
        resp.setCouponDiscount(coupon.discount);
        resp.setCouponName(coupon.name);
        return finalizeResponse(resp, request.getUserId(), coupon.finalPrice);
    }

    private OrderResponse createBatchOrder(OrderRequest request) {
        List<CartItemRequest> cartItems = request.getItems();
        Long userId = request.getUserId();
        String guestId = request.getGuestId() != null ? request.getGuestId() : "anonymous";

        // 第一遍：算总价确定折扣
        List<AssembledItem> assembledList = new ArrayList<>();
        double totalOriginal = 0;
        for (CartItemRequest item : cartItems) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("商品数量必须大于 0");
            }
            AssembledItem assembled = assembleItem(item.getProductCode(), item.getSize(), item.getCondiments());
            totalOriginal += assembled.unitPrice * item.getQuantity();
            assembledList.add(assembled);
        }

        double discountRate = 1.0;
        String strategyName = "游客 (无折扣)";
        if (userId != null && userId > 0) {
            CoffeeUser user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                String level = SpendingDiscountStrategy.getLevel(user.getTotalSpent());
                PricingStrategy strategy = PricingStrategyFactory.getStrategy(level);
                discountRate = strategy.calculatePrice(100) / 100.0;
                strategyName = strategy.getStrategyName() + " (累计消费¥" +
                        String.format("%.0f", user.getTotalSpent()) + ")";
            }
        }

        double memberDiscount = round2(totalOriginal * (1 - discountRate));
        double afterMember = round2(totalOriginal - memberDiscount);
        CouponResult coupon = applyCoupon(request.getCouponCode(), afterMember, userId != null && userId > 0);
        double couponRatio = afterMember == 0 ? 1 : coupon.finalPrice / afterMember;

        // 第二遍：建订单
        List<Map<String, Object>> itemDetails = new ArrayList<>();
        int totalCups = 0;
        double totalFinal = 0;
        for (int i = 0; i < cartItems.size(); i++) {
            CartItemRequest item = cartItems.get(i);
            AssembledItem assembled = assembledList.get(i);
            double unitFinal = round2(assembled.unitPrice * discountRate * couponRatio);
            String condStr = item.getCondiments() != null ? String.join(",", item.getCondiments()) : "";

            for (int q = 0; q < item.getQuantity(); q++) {
                persistAndPublish(assembled, userId, guestId, unitFinal);
            }

            totalCups += item.getQuantity();
            totalFinal += unitFinal * item.getQuantity();

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("productCode", assembled.productCode);
            detail.put("beverageName", assembled.fullName);
            detail.put("categoryCode", assembled.categoryCode);
            detail.put("size", item.getSize());
            detail.put("condiments", condStr);
            detail.put("quantity", item.getQuantity());
            detail.put("unitPrice", unitFinal);
            detail.put("subtotal", round2(unitFinal * item.getQuantity()));
            itemDetails.add(detail);
        }

        // 更新用户累计消费
        totalFinal = coupon.finalPrice;
        double newTotalSpent = 0;
        String memberLevel = "游客";
        int earnedPoints = 0;
        if (userId != null && userId > 0) {
            CoffeeUser user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                user.setTotalSpent(user.getTotalSpent() + totalFinal);
                userRepo.save(user);
                newTotalSpent = user.getTotalSpent();
                memberLevel = SpendingDiscountStrategy.getLevelLabel(newTotalSpent);
                earnedPoints = awardPoints(userId, totalFinal, newTotalSpent);
            }
        }

        OrderResponse resp = new OrderResponse(null, "多品类订单",
                round2(totalOriginal), round2(totalFinal),
                strategyName, "待处理",
                "下单成功！共" + totalCups + "件",
                newTotalSpent, memberLevel);
        resp.setTotalCups(totalCups);
        resp.setItems(itemDetails);
        resp.setMemberDiscount(memberDiscount);
        resp.setCouponDiscount(coupon.discount);
        resp.setCouponName(coupon.name);
        resp.setEarnedPoints(earnedPoints);
        return resp;
    }

    /**
     * 装配一个商品：根据 productCode 找到 Product + 工厂/装饰器，计算 unitPrice
     */
    private AssembledItem assembleItem(String productCode, String size, List<String> condiments) {
        Product product = productRepo.findByCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + productCode));
        if (size == null || size.isBlank()) size = "MEDIUM";

        double unitPrice = product.getBasePrice();
        String fullName = product.getName();
        String categoryCode = product.getCategoryCode();

        // 饮品：走工厂 + 装饰器，basePrice 从数据库注入
        Beverage beverage = factoryRegistry.assemble(productCode, condiments, product.getBasePrice());
        if (beverage != null) {
            beverage.setSize(BevSize.valueOf(size));
            unitPrice = beverage.cost();
            fullName = beverage.getName();
        } else {
            // 甜点/轻食：非饮料，按"份量"加价（不同类型不同规则）
            unitPrice = product.getBasePrice() + extraPriceFor(categoryCode, size);
            fullName = sizeLabel(size) + " " + product.getName();
        }

        AssembledItem item = new AssembledItem();
        item.productCode = productCode;
        item.categoryCode = categoryCode;
        item.fullName = fullName;
        item.unitPrice = unitPrice;
        item.size = size;
        item.condiments = condiments == null ? "" : String.join(",", condiments);
        return item;
    }

    /**
     * 不同分类的份量加价规则
     * - 饮料（coffee/tea/ice/smoothie）：使用 BevSize 的加价
     * - 小吃/轻食（dessert/food）：便宜很多
     */
    private double extraPriceFor(String categoryCode, String size) {
        if ("dessert".equals(categoryCode) || "food".equals(categoryCode)) {
            // 小吃类：份量加价便宜（参考奶茶店小吃加价 0/1/2）
            return switch (size) {
                case "SMALL" -> 0;
                case "LARGE" -> 2;
                default      -> 1;  // MEDIUM
            };
        }
        // 默认（饮品）走原有 BevSize
        return BevSize.valueOf(size).getExtraPrice();
    }

    private String sizeLabel(String size) {
        return switch (size) {
            case "SMALL" -> "小份";
            case "LARGE" -> "大份";
            default -> "中份";
        };
    }

    /**
     * 持久化单条订单
     */
    private OrderResponse persistAndPublish(AssembledItem assembled, long userId,
                                            String guestId, double unitFinal) {
        if (userId > 0) {
            UserOrder order = new UserOrder();
            order.setUserId(userId);
            order.setBeverageName(assembled.fullName);
            order.setSize(assembled.size);
            order.setCondiments(assembled.condiments);
            order.setOriginalPrice(assembled.unitPrice);
            order.setFinalPrice(unitFinal);
            order.setStatus("PENDING");
            order = userOrderRepo.save(order);
            publisher.notifyObservers(order.getId(), assembled.fullName, "PENDING");
            return new OrderResponse(order.getId(), assembled.fullName,
                    assembled.unitPrice, unitFinal, "", "待处理", "下单成功",
                    0, "", assembled.categoryCode);
        } else {
            GuestOrder order = new GuestOrder();
            order.setGuestId(guestId);
            order.setBeverageName(assembled.fullName);
            order.setSize(assembled.size);
            order.setCondiments(assembled.condiments);
            order.setOriginalPrice(assembled.unitPrice);
            order.setFinalPrice(unitFinal);
            order.setStatus("PENDING");
            order = guestOrderRepo.save(order);
            return new OrderResponse(order.getId(), assembled.fullName,
                    assembled.unitPrice, unitFinal, "", "待处理", "下单成功",
                    0, "游客", assembled.categoryCode);
        }
    }

    private OrderResponse finalizeResponse(OrderResponse resp, Long userId, double paidAmount) {
        if (userId == null || userId <= 0) {
            resp.setMemberLevel("游客");
            resp.setPricingStrategy("游客 (无折扣)");
            return resp;
        }
        CoffeeUser user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            resp.setMemberLevel("普通会员");
            resp.setPricingStrategy("普通会员 (无折扣)");
            return resp;
        }
        user.setTotalSpent(user.getTotalSpent() + resp.getFinalPrice());
        userRepo.save(user);
        resp.setTotalSpent(user.getTotalSpent());
        resp.setMemberLevel(SpendingDiscountStrategy.getLevelLabel(user.getTotalSpent()));
        PricingStrategy strategy = PricingStrategyFactory.getStrategy(SpendingDiscountStrategy.getLevel(user.getTotalSpent()));
        resp.setPricingStrategy(strategy.getStrategyName());
        resp.setEarnedPoints(awardPoints(userId, paidAmount, user.getTotalSpent()));
        return resp;
    }

    public List<Map<String, Object>> getUserOrders(Long userId) {
        return userOrderRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::userOrderToMap)
                .toList();
    }

    public List<Map<String, Object>> getGuestOrders(String guestId) {
        return guestOrderRepo.findByGuestIdOrderByCreatedAtDesc(guestId).stream()
                .map(this::guestOrderToMap)
                .toList();
    }

    public List<Map<String, Object>> getAllOrders() {
        List<Map<String, Object>> all = new ArrayList<>();
        userOrderRepo.findAllByOrderByCreatedAtDesc().forEach(o -> {
            Map<String, Object> m = userOrderToMap(o);
            m.put("orderType", "user");
            all.add(m);
        });
        guestOrderRepo.findAllByOrderByCreatedAtDesc().forEach(o -> {
            Map<String, Object> m = guestOrderToMap(o);
            m.put("orderType", "guest");
            all.add(m);
        });
        return all;
    }

    public OrderResponse updateOrderStatus(Long orderId, String action, boolean isUserOrder) {
        if (isUserOrder) {
            UserOrder order = userOrderRepo.findById(orderId).orElse(null);
            if (order == null) return new OrderResponse(null, "", 0, 0, "", "", "订单不存在");
            String newStatus = OrderStateContext.nextState(order.getStatus(), action);
            order.setStatus(newStatus);
            userOrderRepo.save(order);
            publisher.notifyObservers(order.getId(), order.getBeverageName(), newStatus);
            return new OrderResponse(order.getId(), order.getBeverageName(), order.getOriginalPrice(),
                    order.getFinalPrice(), "", newStatus, "状态已更新", 0, "");
        } else {
            GuestOrder order = guestOrderRepo.findById(orderId).orElse(null);
            if (order == null) return new OrderResponse(null, "", 0, 0, "", "", "订单不存在");
            String newStatus = OrderStateContext.nextState(order.getStatus(), action);
            order.setStatus(newStatus);
            guestOrderRepo.save(order);
            publisher.notifyObservers(order.getId(), order.getBeverageName(), newStatus);
            return new OrderResponse(order.getId(), order.getBeverageName(), order.getOriginalPrice(),
                    order.getFinalPrice(), "", newStatus, "状态已更新", 0, "");
        }
    }

    /**
     * 返回菜单数据：所有分类 + 所有商品 + 装饰器配置
     */
    public Map<String, Object> getMenuInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        List<Map<String, Object>> products = productRepo.findByAvailableTrueOrderByCategoryCodeAscIdAsc().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("code", p.getCode());
                    m.put("name", p.getName());
                    m.put("categoryCode", p.getCategoryCode());
                    m.put("basePrice", p.getBasePrice());
                    m.put("description", p.getDescription());
                    m.put("imageUrl", p.getImageUrl());
                    m.put("temperature", p.getTemperature());
                    m.put("allowedCondiments", factoryRegistry.getAllowedCondiments(p.getCode()));
                    return m;
                }).toList();
        info.put("products", products);
        info.put("sizes", List.of("SMALL", "MEDIUM", "LARGE"));
        info.put("observers", publisher.getObserverNames());
        return info;
    }

    /** 会员中心数据。权益和优惠券在服务端统一定义，前端只负责展示。 */
    public Map<String, Object> getMemberDashboard(Long userId) {
        CoffeeUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        MemberPoints account = memberPointsRepo.findByUserId(userId)
                .orElseGet(() -> createPointsAccount(userId, user.getTotalSpent()));
        double spent = user.getTotalSpent();
        double nextThreshold = spent < SpendingDiscountStrategy.VIP_THRESHOLD
                ? SpendingDiscountStrategy.VIP_THRESHOLD
                : (spent < SpendingDiscountStrategy.SVIP_THRESHOLD ? SpendingDiscountStrategy.SVIP_THRESHOLD : SpendingDiscountStrategy.SVIP_THRESHOLD);
        double progress = spent >= SpendingDiscountStrategy.SVIP_THRESHOLD ? 100
                : round2(Math.min(100, spent / nextThreshold * 100));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nickname", user.getNickname());
        result.put("totalSpent", spent);
        result.put("memberLevel", SpendingDiscountStrategy.getLevelLabel(spent));
        result.put("points", account.getPoints());
        result.put("pointsLevel", account.getLevel());
        result.put("nextThreshold", nextThreshold);
        result.put("amountToNext", Math.max(0, round2(nextThreshold - spent)));
        result.put("progress", progress);
        result.put("coupons", List.of(
                couponInfo("FIKA8", "下午茶立减 ¥8", 48, 8, "全品类可用 · 会员权益券"),
                couponInfo("SWEET12", "甜品满 ¥78 减 ¥12", 78, 12, "咖啡、轻食和甜点一起享"),
                couponInfo("BEAN15", "咖啡满 ¥88 减 ¥15", 88, 15, "适合和朋友一起点")));
        return result;
    }

    private Map<String, Object> couponInfo(String code, String name, double minimum, double discount, String description) {
        Map<String, Object> coupon = new LinkedHashMap<>();
        coupon.put("code", code);
        coupon.put("name", name);
        coupon.put("minimum", minimum);
        coupon.put("discount", discount);
        coupon.put("description", description);
        return coupon;
    }

    private double memberDiscountRate(Long userId) {
        if (userId == null || userId <= 0) return 1.0;
        return userRepo.findById(userId)
                .map(user -> PricingStrategyFactory.getStrategy(SpendingDiscountStrategy.getLevel(user.getTotalSpent()))
                        .calculatePrice(100) / 100.0)
                .orElse(1.0);
    }

    private CouponResult applyCoupon(String code, double amount, boolean isMember) {
        if (!isMember || code == null || code.isBlank()) return new CouponResult(amount, 0, "");
        return switch (code.trim().toUpperCase()) {
            case "FIKA8" -> fixedCoupon(amount, 48, 8, "下午茶立减 ¥8");
            case "SWEET12" -> fixedCoupon(amount, 78, 12, "甜品满 ¥78 减 ¥12");
            case "BEAN15" -> fixedCoupon(amount, 88, 15, "咖啡满 ¥88 减 ¥15");
            default -> new CouponResult(amount, 0, "");
        };
    }

    private CouponResult fixedCoupon(double amount, double minimum, double discount, String name) {
        if (amount + 1e-9 < minimum) return new CouponResult(amount, 0, "");
        return new CouponResult(round2(Math.max(0, amount - discount)), discount, name);
    }

    private int awardPoints(Long userId, double paidAmount, double totalSpent) {
        if (userId == null || userId <= 0) return 0;
        int earned = Math.max(0, (int) Math.floor(paidAmount));
        MemberPoints account = memberPointsRepo.findByUserId(userId)
                .orElseGet(() -> createPointsAccount(userId, totalSpent - paidAmount));
        account.setPoints(account.getPoints() + earned);
        account.setLevel(pointsLevel(account.getPoints()));
        memberPointsRepo.save(account);
        return earned;
    }

    private MemberPoints createPointsAccount(Long userId, double totalSpent) {
        MemberPoints account = new MemberPoints();
        account.setUserId(userId);
        account.setPoints(Math.max(0, (int) Math.floor(totalSpent)));
        account.setLevel(pointsLevel(account.getPoints()));
        return memberPointsRepo.save(account);
    }

    private String pointsLevel(int points) {
        if (points >= 1000) return "GOLD";
        if (points >= 500) return "SILVER";
        return "BRONZE";
    }

    private Map<String, Object> userOrderToMap(UserOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("userId", o.getUserId());
        m.put("beverageName", o.getBeverageName());
        m.put("size", o.getSize());
        m.put("condiments", o.getCondiments());
        m.put("originalPrice", o.getOriginalPrice());
        m.put("finalPrice", o.getFinalPrice());
        m.put("status", o.getStatus());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }

    private Map<String, Object> guestOrderToMap(GuestOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("guestId", o.getGuestId());
        m.put("beverageName", o.getBeverageName());
        m.put("size", o.getSize());
        m.put("condiments", o.getCondiments());
        m.put("originalPrice", o.getOriginalPrice());
        m.put("finalPrice", o.getFinalPrice());
        m.put("status", o.getStatus());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record CouponResult(double finalPrice, double discount, String name) {}

    /**
     * 内部类：装配好的商品单元
     */
    private static class AssembledItem {
        String productCode;
        String categoryCode;
        String fullName;
        double unitPrice;
        String size;
        String condiments;
    }
}
