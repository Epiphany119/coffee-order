package com.coffee.module.order.biz.application.service;

import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.api.dto.*;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.coffee.module.order.biz.domain.service.OrderDomainService;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.util.MoneyUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单应用服务
 */
@Service
public class OrderApplicationService implements OrderService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final MenuService productService;
    private final MemberService memberService;
    private final MembershipService membershipService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderApplicationService(OrderDomainService orderDomainService,
                                  OrderRepository orderRepository,
                                  MenuService productService,
                                  MemberService memberService,
                                  MembershipService membershipService,
                                  ApplicationEventPublisher eventPublisher) {
        this.orderDomainService = orderDomainService;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.memberService = memberService;
        this.membershipService = membershipService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderCommand command) {
        if (command.isBatch()) {
            return createBatchOrder(command);
        }
        return createSingleOrder(command);
    }

    private OrderResponse createSingleOrder(CreateOrderCommand command) {
        Long storeId = command.getStoreId();
        MenuItemDTO product = productService.getProductByCode(storeId, command.getProductCode());
        double unitPrice = productService.calculatePrice(
                storeId,
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCustomSize(),
                command.getCondiments());
        String fullName = productService.getDisplayName(
                storeId,
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCustomSize(),
                command.getCondiments());

        Long userId = command.getUserId();
        String guestId = command.getGuestId();
        String size = command.getSize() != null ? command.getSize() : "MEDIUM";
        String customSize = command.getCustomSize();
        String condiments = command.getCondiments() != null ? String.join(",", command.getCondiments()) : "";

        double discountRate = 1.0;
        String strategyName = "游客 (无折扣)";
        String memberLevel = "游客";
        double memberDiscount = 0;

        if (userId != null && userId > 0) {
            MemberLevelDTO levelDTO = memberService.getMemberLevel(userId);
            discountRate = levelDTO.getDiscountRate();
            strategyName = levelDTO.getLabel() + " (累计消费¥" + levelDTO.getTotalSpent() + ")";
            memberLevel = levelDTO.getLabel();
            memberDiscount = MoneyUtils.round2(unitPrice * (1 - discountRate));
        }

        double afterMember = MoneyUtils.round2(unitPrice * discountRate);
        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember, userId != null && userId > 0);

        LocalDateTime readyTime = orderDomainService.calculateReadyTime();
        String readyTimeStr = orderDomainService.formatReadyTime(readyTime);

        Order order = orderDomainService.createOrder(userId, guestId, fullName, size, customSize,
                condiments, unitPrice, coupon.finalPrice, product.getCategoryCode());
        order.setStoreId(command.getStoreId());
        order.setFulfillmentType(command.getFulfillmentType());
        order.setNote(command.getNote());
        order.setStrategyName(strategyName);
        order.setMemberLevel(memberLevel);
        order.setMemberDiscount(memberDiscount);
        order.setCouponName(coupon.name);
        order.setCouponDiscount(coupon.discount);
        order.setTotalCups(1);

        order = orderRepository.save(order);
        eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));

        // 消费累计延后到订单完成时（COMPLETED）执行，取消/未完成的订单不计入累计消费

        return buildResponse(order, product.getCategoryCode(), coupon, readyTimeStr,
                unitPrice, memberDiscount, 0, "下单成功，预计 " + OrderDomainService.DEFAULT_PREPARE_MINUTES + " 分钟后可取餐");
    }

    private OrderResponse createBatchOrder(CreateOrderCommand command) {
        List<CartItemCommand> cartItems = command.getItems();
        Long userId = command.getUserId();
        String guestId = command.getGuestId() != null ? command.getGuestId() : "anonymous";

        // 第一遍：展开商品行（每件一行）并计算原价总额
        List<OrderLine> lines = new ArrayList<>();
        double totalOriginal = 0;
        for (CartItemCommand item : cartItems) {
            if (item.getQuantity() <= 0) {
                throw new ServiceException(400, "商品数量必须大于 0");
            }
            MenuItemDTO product = productService.getProductByCode(command.getStoreId(), item.getProductCode());
            double unitPrice = productService.calculatePrice(
                    command.getStoreId(),
                    item.getProductCode(),
                    item.getSize() != null ? item.getSize() : "MEDIUM",
                    item.getCustomSize(),
                    item.getCondiments());
            String fullName = productService.getDisplayName(command.getStoreId(),
                    item.getProductCode(),
                    item.getSize() != null ? item.getSize() : "MEDIUM",
                    item.getCustomSize(), item.getCondiments());
            String condiments = item.getCondiments() != null ? String.join(",", item.getCondiments()) : "";
            for (int q = 0; q < item.getQuantity(); q++) {
                lines.add(new OrderLine(fullName, item.getProductCode(),
                        item.getSize() != null ? item.getSize() : "MEDIUM",
                        item.getCustomSize(),
                        condiments, unitPrice, product.getCategoryCode()));
            }
            totalOriginal += unitPrice * item.getQuantity();
        }

        // 会员折扣（按总额）→ 优惠券（在会员价基础上判断门槛）
        double discountRate = 1.0;
        String strategyName = "游客 (无折扣)";
        String memberLevel = "游客";
        double memberDiscount = 0;

        if (userId != null && userId > 0) {
            MemberLevelDTO levelDTO = memberService.getMemberLevel(userId);
            discountRate = levelDTO.getDiscountRate();
            strategyName = levelDTO.getLabel() + " (累计消费¥" + levelDTO.getTotalSpent() + ")";
            memberLevel = levelDTO.getLabel();
            memberDiscount = MoneyUtils.round2(totalOriginal * (1 - discountRate));
        }

        double afterMember = MoneyUtils.round2(totalOriginal - memberDiscount);
        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember, userId != null && userId > 0);
        double finalTotal = coupon.finalPrice;
        double totalDiscount = MoneyUtils.round2(totalOriginal - finalTotal);

        // 第二遍：按原价比例把总折扣分摊到每件商品（尾差归最后一件），逐件落库，
        // 保证订单明细的折后价合计 = 实付总额，优惠券/会员折扣真正生效
        double allocated = 0;
        int cups = 0;
        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            OrderLine line = lines.get(i);
            double lineDiscount;
            if (i == lines.size() - 1) {
                lineDiscount = MoneyUtils.round2(totalDiscount - allocated);
            } else {
                lineDiscount = MoneyUtils.round2(line.unitPrice / totalOriginal * totalDiscount);
            }
            allocated += lineDiscount;
            double lineFinal = MoneyUtils.round2(line.unitPrice - lineDiscount);

            Order order = orderDomainService.createOrder(userId, guestId, line.fullName,
                    line.size, line.customSize, line.condiments, line.unitPrice, lineFinal, line.categoryCode);
            order.setStoreId(command.getStoreId());
            order.setFulfillmentType(command.getFulfillmentType());
            order.setNote(command.getNote());
            order = orderRepository.save(order);
            eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));
            cups++;

            OrderResponse.OrderItemResponse itemResp = new OrderResponse.OrderItemResponse();
            itemResp.setProductCode(line.productCode);
            itemResp.setUnitPrice(line.unitPrice);
            itemResp.setQuantity(1);
            itemResp.setSubtotal(lineFinal);
            itemResponses.add(itemResp);
        }

        int earnedPoints = 0;
        if (userId != null && userId > 0) {
            // 消费累计延后到订单完成时（COMPLETED）执行，这里只预估本次可得积分用于展示
            earnedPoints = orderDomainService.calculateEarnedPoints(finalTotal);
        }

        OrderResponse response = new OrderResponse();
        response.setOrderName("多品类订单");
        response.setOriginalPrice(MoneyUtils.round2(totalOriginal));
        response.setFinalPrice(finalTotal);
        response.setPricingStrategy(strategyName);
        response.setStatus("待处理");
        response.setMessage("下单成功！共" + cups + "件，预计" + OrderDomainService.DEFAULT_PREPARE_MINUTES + "分钟后可取餐");
        response.setMemberLevel(memberLevel);
        response.setTotalCups(cups);
        response.setItems(itemResponses);
        response.setMemberDiscount(memberDiscount);
        response.setCouponDiscount(coupon.discount);
        response.setCouponName(coupon.name);
        response.setEarnedPoints(earnedPoints);
        response.setEstimatedReadyTime(orderDomainService.formatReadyTime(orderDomainService.calculateReadyTime()));
        return response;
    }

    /** 批量下单的商品行（展开后的单件） */
    private record OrderLine(String fullName, String productCode, String size, String customSize,
                             String condiments, double unitPrice, String categoryCode) {}

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String action, boolean isUserOrder) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        Order.OrderStatus newStatus = orderDomainService.calculateNextStatus(order.getStatus().name(), action);
        orderRepository.updateStatus(orderId, newStatus);
        eventPublisher.publishEvent(OrderDomainEvent.statusChanged(orderId, order.getBeverageName(), newStatus.name()));
        // 消费累计 + 积分入账：仅在订单完成的那一刻计入（状态机单向，只会触发一次），取消/未完成不计入
        if (newStatus == Order.OrderStatus.COMPLETED && order.getStatus() != Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.addSpending(order.getUserId(), order.getFinalPrice());
            membershipService.addConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }
        // 取消回滚：已完成订单被取消时，累计消费与积分按实付金额扣回（先扣消费，积分按回滚后重算）
        if (newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.subtractSpending(order.getUserId(), order.getFinalPrice());
            membershipService.deductConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }

        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderName(order.getBeverageName());
        response.setStatus(newStatus.getDescription());
        response.setMessage("状态已更新");
        return response;
    }

    @Override
    @Transactional
    public OrderResponse updateStoreOrderStatus(Long orderId, String action, Long storeId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        if (storeId == null || !storeId.equals(order.getStoreId())) {
            throw new ServiceException(403, "订单不属于该店铺，无权操作");
        }
        Order.OrderStatus newStatus = orderDomainService.calculateNextStatus(order.getStatus().name(), action);
        orderRepository.updateStatus(orderId, newStatus);
        eventPublisher.publishEvent(OrderDomainEvent.statusChanged(orderId, order.getBeverageName(), newStatus.name()));
        // 消费累计 + 积分入账：仅在订单完成的那一刻计入（状态机单向，只会触发一次），取消/未完成不计入
        if (newStatus == Order.OrderStatus.COMPLETED && order.getStatus() != Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.addSpending(order.getUserId(), order.getFinalPrice());
            membershipService.addConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }
        // 取消回滚：已完成订单被取消时，累计消费与积分按实付金额扣回（先扣消费，积分按回滚后重算）
        if (newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.subtractSpending(order.getUserId(), order.getFinalPrice());
            membershipService.deductConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }

        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderName(order.getBeverageName());
        response.setStatus(newStatus.getDescription());
        response.setMessage("状态已更新");
        return response;
    }

    @Override
    public List<Map<String, Object>> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toMap).toList();
    }

    @Override
    public List<Map<String, Object>> getGuestOrders(String guestId) {
        return orderRepository.findByGuestId(guestId).stream().map(this::toMap).toList();
    }

    @Override
    public List<Map<String, Object>> getAllOrders() {
        return orderRepository.findAll().stream().map(order -> {
            Map<String, Object> m = toMap(order);
            m.put("orderType", order.isUserOrder() ? "user" : "guest");
            return m;
        }).toList();
    }

    @Override
    public Map<String, Object> getMenuInfo(Long storeId) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (storeId != null) {
            info.put("products", productService.getAllProducts(storeId));
        } else {
            info.put("products", List.of());
        }
        info.put("sizes", List.of("SMALL", "MEDIUM", "LARGE", "CUSTOM"));
        // 定制规格计价规则（前端展示/计算参考，实际计价以服务端为准）
        Map<String, Object> customRule = new LinkedHashMap<>();
        customRule.put("baseMl", MenuService.CUSTOM_BASE_ML);
        customRule.put("baseG", MenuService.CUSTOM_BASE_G);
        info.put("customRule", customRule);
        // 店铺可见类目：共享类目 + 该店自定义类目（前端 tab 渲染用）
        info.put("categories", storeId != null ? productService.listCategories(storeId) : List.of());
        return info;
    }

    @Override
    public double getTotalSaved(Long userId) {
        if (userId == null || userId <= 0) return 0;
        double saved = orderRepository.findByUserId(userId).stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.COMPLETED)
                .mapToDouble(o -> Math.max(0, o.getOriginalPrice() - o.getFinalPrice()))
                .sum();
        return MoneyUtils.round2(saved);
    }

    @Override
    public List<Map<String, Object>> getStoreOrders(Long storeId, String status) {
        List<Order> orders = (status == null || status.isBlank())
                ? orderRepository.findByStoreId(storeId)
                : orderRepository.findByStoreIdAndStatus(storeId, status);
        return orders.stream().map(order -> {
            Map<String, Object> m = toMap(order);
            m.put("orderType", order.isUserOrder() ? "user" : "guest");
            return m;
        }).toList();
    }

    @Override
    public Map<String, Object> getStoreStats(Long storeId) {
        Map<String, Object> today = orderRepository.todayStats(storeId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("todayRevenue", today.get("revenue"));
        stats.put("todayOrders", today.get("cnt"));
        stats.put("pendingOrders", orderRepository.countPendingByStoreId(storeId));
        stats.put("weekSales", orderRepository.weekStats(storeId));
        return stats;
    }

    private OrderResponse buildResponse(Order order, String categoryCode, CouponResult coupon,
                                       String readyTimeStr, double original, double memberDiscount,
                                       int earnedPoints, String message) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderName(order.getBeverageName());
        response.setOriginalPrice(MoneyUtils.round2(original));
        response.setFinalPrice(coupon.finalPrice);
        response.setPricingStrategy(coupon.name.isEmpty() ? "游客 (无折扣)" : coupon.name);
        response.setStatus("待处理");
        response.setMessage(message);
        response.setMemberLevel("游客");
        response.setCategoryCode(categoryCode);
        response.setTotalCups(1);
        response.setMemberDiscount(memberDiscount);
        response.setCouponDiscount(coupon.discount);
        response.setCouponName(coupon.name);
        response.setEarnedPoints(earnedPoints);
        response.setEstimatedReadyTime(readyTimeStr);
        return response;
    }

    private Map<String, Object> toMap(Order order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId());
        m.put("userId", order.getUserId());
        m.put("guestId", order.getGuestId());
        m.put("storeId", order.getStoreId());
        m.put("fulfillmentType", order.getFulfillmentType());
        m.put("note", order.getNote());
        m.put("beverageName", order.getBeverageName());
        m.put("size", order.getSize());
        m.put("customSize", order.getCustomSize());
        m.put("condiments", order.getCondiments());
        m.put("originalPrice", order.getOriginalPrice());
        m.put("finalPrice", order.getFinalPrice());
        m.put("status", order.getStatus().name());
        m.put("createdAt", order.getCreatedAt());
        m.put("estimatedReadyTime", order.getEstimatedReadyTime());
        return m;
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
        return new CouponResult(MoneyUtils.round2(Math.max(0, amount - discount)), discount, name);
    }

    private record CouponResult(double finalPrice, double discount, String name) {}
}
