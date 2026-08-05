package com.coffee.module.order.biz.application.service;

import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.api.dto.*;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.coffee.module.order.biz.domain.service.OrderDomainService;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.module.product.api.ProductService;
import com.coffee.module.product.api.dto.ProductDTO;
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
    private final ProductService productService;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderApplicationService(OrderDomainService orderDomainService,
                                  OrderRepository orderRepository,
                                  ProductService productService,
                                  MemberService memberService,
                                  ApplicationEventPublisher eventPublisher) {
        this.orderDomainService = orderDomainService;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.memberService = memberService;
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
        ProductDTO product = productService.getProductByCode(command.getProductCode());
        double unitPrice = productService.calculatePrice(
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCondiments());
        String fullName = productService.getDisplayName(
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCondiments());

        Long userId = command.getUserId();
        String guestId = command.getGuestId();
        String size = command.getSize() != null ? command.getSize() : "MEDIUM";
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

        Order order = orderDomainService.createOrder(userId, guestId, fullName, size, condiments,
                unitPrice, coupon.finalPrice, product.getCategoryCode());
        order.setStrategyName(strategyName);
        order.setMemberLevel(memberLevel);
        order.setMemberDiscount(memberDiscount);
        order.setCouponName(coupon.name);
        order.setCouponDiscount(coupon.discount);
        order.setTotalCups(1);

        order = orderRepository.save(order);
        eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));

        if (userId != null && userId > 0) {
            memberService.addSpending(userId, coupon.finalPrice);
        }

        return buildResponse(order, product.getCategoryCode(), coupon, readyTimeStr,
                unitPrice, memberDiscount, 0, "下单成功，预计 " + OrderDomainService.DEFAULT_PREPARE_MINUTES + " 分钟后可取餐");
    }

    private OrderResponse createBatchOrder(CreateOrderCommand command) {
        List<CartItemCommand> cartItems = command.getItems();
        Long userId = command.getUserId();
        String guestId = command.getGuestId() != null ? command.getGuestId() : "anonymous";

        double totalOriginal = 0;
        int totalCups = 0;
        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();

        for (CartItemCommand item : cartItems) {
            if (item.getQuantity() <= 0) {
                throw new ServiceException(400, "商品数量必须大于 0");
            }
            ProductDTO product = productService.getProductByCode(item.getProductCode());
            double unitPrice = productService.calculatePrice(
                    item.getProductCode(),
                    item.getSize() != null ? item.getSize() : "MEDIUM",
                    item.getCondiments());

            for (int q = 0; q < item.getQuantity(); q++) {
                String fullName = productService.getDisplayName(item.getProductCode(),
                        item.getSize() != null ? item.getSize() : "MEDIUM", item.getCondiments());
                String condiments = item.getCondiments() != null ? String.join(",", item.getCondiments()) : "";

                Order order = orderDomainService.createOrder(userId, guestId, fullName,
                        item.getSize() != null ? item.getSize() : "MEDIUM",
                        condiments, unitPrice, unitPrice, product.getCategoryCode());
                order = orderRepository.save(order);
                eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));
            }

            totalOriginal += unitPrice * item.getQuantity();
            totalCups += item.getQuantity();

            OrderResponse.OrderItemResponse itemResp = new OrderResponse.OrderItemResponse();
            itemResp.setProductCode(item.getProductCode());
            itemResp.setUnitPrice(unitPrice);
            itemResp.setQuantity(item.getQuantity());
            itemResp.setSubtotal(MoneyUtils.round2(unitPrice * item.getQuantity()));
            itemResponses.add(itemResp);
        }

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

        int earnedPoints = 0;
        if (userId != null && userId > 0) {
            memberService.addSpending(userId, coupon.finalPrice);
            earnedPoints = orderDomainService.calculateEarnedPoints(coupon.finalPrice);
        }

        OrderResponse response = new OrderResponse();
        response.setOrderName("多品类订单");
        response.setOriginalPrice(MoneyUtils.round2(totalOriginal));
        response.setFinalPrice(coupon.finalPrice);
        response.setPricingStrategy(strategyName);
        response.setStatus("待处理");
        response.setMessage("下单成功！共" + totalCups + "件，预计" + OrderDomainService.DEFAULT_PREPARE_MINUTES + "分钟后可取餐");
        response.setMemberLevel(memberLevel);
        response.setTotalCups(totalCups);
        response.setItems(itemResponses);
        response.setMemberDiscount(memberDiscount);
        response.setCouponDiscount(coupon.discount);
        response.setCouponName(coupon.name);
        response.setEarnedPoints(earnedPoints);
        response.setEstimatedReadyTime(orderDomainService.formatReadyTime(orderDomainService.calculateReadyTime()));
        return response;
    }

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
    public Map<String, Object> getMenuInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("products", productService.getAllProducts());
        info.put("sizes", List.of("SMALL", "MEDIUM", "LARGE"));
        return info;
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
        return new CouponResult(MoneyUtils.round2(Math.max(0, amount - discount)), discount, name);
    }

    private record CouponResult(double finalPrice, double discount, String name) {}
}
