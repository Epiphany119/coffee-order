package com.coffee.order.application.service;

import com.coffee.order.application.dto.*;
import com.coffee.order.domain.member.service.MemberDomainService;
import com.coffee.order.domain.member.valueobject.MemberLevel;
import com.coffee.order.domain.order.aggregate.OrderAggregate;
import com.coffee.order.domain.order.entity.OrderStatus;
import com.coffee.order.domain.order.event.OrderDomainEvent;
import com.coffee.order.domain.order.repository.OrderRepository;
import com.coffee.order.domain.order.service.OrderDomainService;
import com.coffee.order.domain.order.valueobject.Money;
import com.coffee.order.domain.order.valueobject.OrderLineItem;
import com.coffee.order.domain.product.entity.Product;
import com.coffee.order.domain.product.service.ProductDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单应用服务
 */
@Service
public class OrderApplicationService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final ProductDomainService productDomainService;
    private final MemberDomainService memberDomainService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderApplicationService(OrderDomainService orderDomainService,
                                   OrderRepository orderRepository,
                                   ProductDomainService productDomainService,
                                   MemberDomainService memberDomainService,
                                   ApplicationEventPublisher eventPublisher) {
        this.orderDomainService = orderDomainService;
        this.orderRepository = orderRepository;
        this.productDomainService = productDomainService;
        this.memberDomainService = memberDomainService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponseDTO createOrder(CreateOrderCommand command) {
        if (command.isBatch()) {
            return createBatchOrder(command);
        }
        return createSingleOrder(command);
    }

    private OrderResponseDTO createSingleOrder(CreateOrderCommand command) {
        Product product = productDomainService.findProductByCode(command.getProductCode());
        double unitPrice = productDomainService.calculatePrice(product,
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCondiments());
        String fullName = productDomainService.getDisplayName(product,
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCondiments());

        long userId = command.getUserId() != null ? command.getUserId() : 0;
        double discountRate = memberDomainService.getDiscountRate(command.getUserId());
        double afterMember = round2(unitPrice * discountRate);

        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember,
                command.getUserId() != null && command.getUserId() > 0);

        LocalDateTime readyTime = LocalDateTime.now().plusMinutes(OrderDomainService.DEFAULT_PREPARE_MINUTES);
        String readyTimeStr = formatTime(readyTime);

        OrderLineItem lineItem = OrderLineItem.create(
                command.getProductCode(), fullName, product.getCategoryCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCondiments(), 1, unitPrice, readyTime);

        OrderAggregate order = orderDomainService.createOrder(
                command.getUserId(), command.getGuestId(), List.of(lineItem),
                Money.withDiscount(unitPrice, coupon.discount));

        order = orderRepository.save(order);

        OrderDomainEvent event = orderDomainService.whenOrderCreated(order);
        eventPublisher.publishEvent(event);

        if (command.getUserId() != null && command.getUserId() > 0) {
            memberDomainService.addSpending(command.getUserId(), coupon.finalPrice);
        }

        return buildResponse(order, product.getCategoryCode(), coupon, readyTimeStr,
                unitPrice, afterMember, 0, "下单成功，预计 " + OrderDomainService.DEFAULT_PREPARE_MINUTES + " 分钟后可取餐");
    }

    private OrderResponseDTO createBatchOrder(CreateOrderCommand command) {
        List<CartItemCommand> cartItems = command.getItems();
        Long userId = command.getUserId();
        String guestId = command.getGuestId() != null ? command.getGuestId() : "anonymous";

        List<OrderLineItem> lineItems = new ArrayList<>();
        double totalOriginal = 0;
        LocalDateTime readyTime = LocalDateTime.now().plusMinutes(OrderDomainService.DEFAULT_PREPARE_MINUTES);

        for (CartItemCommand item : cartItems) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("商品数量必须大于 0");
            }
            OrderLineItem lineItem = orderDomainService.assembleLineItem(
                    item.getProductCode(),
                    item.getSize() != null ? item.getSize() : "MEDIUM",
                    item.getCondiments(),
                    item.getQuantity());
            lineItems.add(lineItem);
            totalOriginal += lineItem.unitPrice() * item.getQuantity();
        }

        double discountRate = 1.0;
        String strategyName = "游客 (无折扣)";
        if (userId != null && userId > 0) {
            MemberLevel level = memberDomainService.getMemberLevel(userId);
            discountRate = level.discountRate();
            double totalSpent = memberDomainService.getTotalSpent(userId);
            strategyName = level.label() + " (累计消费¥" + String.format("%.0f", totalSpent) + ")";
        }

        double memberDiscount = round2(totalOriginal * (1 - discountRate));
        double afterMember = round2(totalOriginal - memberDiscount);

        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember,
                userId != null && userId > 0);

        List<OrderItemDTO> itemDTOs = new ArrayList<>();
        double couponRatio = afterMember == 0 ? 1 : coupon.finalPrice / afterMember;
        int totalCups = 0;

        for (int i = 0; i < lineItems.size(); i++) {
            OrderLineItem lineItem = lineItems.get(i);
            CartItemCommand cartItem = cartItems.get(i);
            double unitFinal = round2(lineItem.unitPrice() * discountRate * couponRatio);

            OrderItemDTO dto = new OrderItemDTO();
            dto.setProductCode(lineItem.productCode());
            dto.setBeverageName(lineItem.beverageName());
            dto.setCategoryCode(lineItem.categoryCode());
            dto.setSize(lineItem.size());
            dto.setCondiments(String.join(",", lineItem.condiments()));
            dto.setQuantity(cartItem.getQuantity());
            dto.setUnitPrice(unitFinal);
            dto.setSubtotal(round2(unitFinal * cartItem.getQuantity()));
            dto.setEstimatedReadyTime(formatTime(readyTime));
            itemDTOs.add(dto);

            for (int q = 0; q < cartItem.getQuantity(); q++) {
                OrderLineItem singleItem = OrderLineItem.create(
                        lineItem.productCode(), lineItem.beverageName(),
                        lineItem.categoryCode(), lineItem.size(),
                        lineItem.condiments(), 1, lineItem.unitPrice(), readyTime);
                OrderAggregate singleOrder = orderDomainService.createOrder(
                        userId, guestId, List.of(singleItem),
                        Money.withDiscount(lineItem.unitPrice(), lineItem.unitPrice() - unitFinal));
                singleOrder = orderRepository.save(singleOrder);

                OrderDomainEvent event = orderDomainService.whenOrderCreated(singleOrder);
                eventPublisher.publishEvent(event);
            }

            totalCups += cartItem.getQuantity();
        }

        double totalFinal = coupon.finalPrice;
        double newTotalSpent = 0;
        String memberLevel = "游客";
        int earnedPoints = 0;

        if (userId != null && userId > 0) {
            memberDomainService.addSpending(userId, totalFinal);
            newTotalSpent = memberDomainService.getTotalSpent(userId);
            MemberLevel level = MemberLevel.fromTotalSpent(newTotalSpent);
            memberLevel = level.label();
            earnedPoints = orderDomainService.calculateEarnedPoints(totalFinal);
        }

        OrderResponseDTO response = new OrderResponseDTO();
        response.setOrderId(null);
        response.setOrderName("多品类订单");
        response.setOriginalPrice(round2(totalOriginal));
        response.setFinalPrice(round2(totalFinal));
        response.setPricingStrategy(strategyName);
        response.setStatus("待处理");
        response.setMessage("下单成功！共" + totalCups + "件，预计" + OrderDomainService.DEFAULT_PREPARE_MINUTES + "分钟后可取餐");
        response.setTotalSpent(newTotalSpent);
        response.setMemberLevel(memberLevel);
        response.setTotalCups(totalCups);
        response.setItems(itemDTOs);
        response.setMemberDiscount(memberDiscount);
        response.setCouponDiscount(coupon.discount);
        response.setCouponName(coupon.name);
        response.setEarnedPoints(earnedPoints);
        response.setEstimatedReadyTime(formatTime(readyTime));

        return response;
    }

    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, String action, boolean isUserOrder) {
        OrderAggregate order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        OrderStatus newStatus = orderDomainService.calculateNextStatus(order.getStatus().name(), action);
        order.transitionTo(newStatus);
        orderRepository.updateStatus(orderId, newStatus);

        OrderDomainEvent event = orderDomainService.whenOrderStatusChanged(order, newStatus);
        eventPublisher.publishEvent(event);

        OrderResponseDTO response = new OrderResponseDTO();
        response.setOrderId(order.getId());
        response.setOrderName(order.getBeverageName());
        response.setStatus(newStatus.getDescription());
        response.setMessage("状态已更新");
        return response;
    }

    public List<Map<String, Object>> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::orderToMap)
                .toList();
    }

    public List<Map<String, Object>> getGuestOrders(String guestId) {
        return orderRepository.findByGuestId(guestId).stream()
                .map(this::orderToMap)
                .toList();
    }

    public List<Map<String, Object>> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(order -> {
                    Map<String, Object> m = orderToMap(order);
                    m.put("orderType", order.isUserOrder() ? "user" : "guest");
                    return m;
                })
                .toList();
    }

    public Map<String, Object> getMenuInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        List<Map<String, Object>> products = productDomainService.getAllAvailableProducts().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("code", p.getCode());
                    m.put("name", p.getName());
                    m.put("categoryCode", p.getCategoryCode());
                    m.put("basePrice", p.getBasePriceAsDouble());
                    m.put("description", p.getDescription());
                    m.put("imageUrl", p.getImageUrl());
                    m.put("temperature", p.getTemperature());
                    return m;
                }).toList();
        info.put("products", products);
        info.put("sizes", List.of("SMALL", "MEDIUM", "LARGE"));
        return info;
    }

    private OrderResponseDTO buildResponse(OrderAggregate order, String categoryCode,
                                            CouponResult coupon, String readyTimeStr,
                                            double original, double afterMember,
                                            int earnedPoints, String message) {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setOrderId(order.getId());
        response.setOrderName(order.getBeverageName());
        response.setOriginalPrice(round2(original));
        response.setFinalPrice(round2(coupon.finalPrice));
        response.setPricingStrategy(coupon.name.isEmpty() ? "游客 (无折扣)" : coupon.name);
        response.setStatus("待处理");
        response.setMessage(message);
        response.setTotalSpent(0);
        response.setMemberLevel("游客");
        response.setCategoryCode(categoryCode);
        response.setTotalCups(1);
        response.setMemberDiscount(round2(original - afterMember));
        response.setCouponDiscount(coupon.discount);
        response.setCouponName(coupon.name);
        response.setEarnedPoints(earnedPoints);
        response.setEstimatedReadyTime(readyTimeStr);
        return response;
    }

    private Map<String, Object> orderToMap(OrderAggregate order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId());
        m.put("beverageName", order.getBeverageName());
        m.put("size", order.getSize());
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
        return new CouponResult(round2(Math.max(0, amount - discount)), discount, name);
    }

    private String formatTime(LocalDateTime time) {
        return time.toLocalDate() + " " + time.toLocalTime().withSecond(0).withNano(0);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record CouponResult(double finalPrice, double discount, String name) {}
}
