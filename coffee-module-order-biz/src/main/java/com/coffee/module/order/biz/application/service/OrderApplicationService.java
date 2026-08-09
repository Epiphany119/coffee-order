package com.coffee.module.order.biz.application.service;

import com.coffee.module.order.api.OrderService;
import com.coffee.module.inventory.api.InventoryService;
import com.coffee.module.marketing.api.FlashSaleService;
import com.coffee.module.order.api.dto.*;
import com.coffee.module.order.biz.domain.Order;
import com.coffee.module.order.biz.domain.OrderItem;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import com.coffee.module.order.biz.domain.repository.OrderRepository;
import com.coffee.module.order.biz.domain.service.OrderDomainService;
import com.coffee.module.member.api.MemberService;
import com.coffee.module.member.api.dto.MemberLevelDTO;
import com.coffee.module.membership.api.MembershipService;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.util.MoneyUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final StoreService storeService;
    private final MerchantService merchantService;
    private final ApplicationEventPublisher eventPublisher;
    private final InventoryService inventoryService;
    private final FlashSaleService flashSaleService;
    private final boolean asyncMembershipEnabled;

    public OrderApplicationService(OrderDomainService orderDomainService,
                                  OrderRepository orderRepository,
                                  MenuService productService,
                                  MemberService memberService,
                                  MembershipService membershipService,
                                  StoreService storeService,
                                  MerchantService merchantService,
                                  ApplicationEventPublisher eventPublisher,
                                  InventoryService inventoryService,
                                  FlashSaleService flashSaleService,
                                  @Value("${coffee.membership.async-enabled:false}") boolean asyncMembershipEnabled) {
        this.orderDomainService = orderDomainService;
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.memberService = memberService;
        this.membershipService = membershipService;
        this.storeService = storeService;
        this.merchantService = merchantService;
        this.eventPublisher = eventPublisher;
        this.inventoryService = inventoryService;
        this.flashSaleService = flashSaleService;
        this.asyncMembershipEnabled = asyncMembershipEnabled;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderCommand command) {
        if (command.getFlashSaleClaimNo() != null && !command.getFlashSaleClaimNo().isBlank() && command.isBatch()) {
            if (command.getItems().size() != 1 || command.getItems().get(0).getQuantity() != 1) {
                throw new ServiceException(400, "秒杀商品请单独结算，且每次限购一件");
            }
            CartItemCommand item = command.getItems().get(0);
            command.setProductCode(item.getProductCode()); command.setSize(item.getSize());
            command.setCustomSize(item.getCustomSize()); command.setCondiments(item.getCondiments()); command.setItems(null);
        }
        if (command.isBatch()) {
            return createBatchOrder(command);
        }
        return createSingleOrder(command);
    }

    private OrderResponse createSingleOrder(CreateOrderCommand command) {
        Long storeId = command.getStoreId();
        Long userId = command.getUserId();
        String guestId = command.getGuestId();
        MenuItemDTO product = productService.getProductByCode(storeId, command.getProductCode());
        double regularUnitPrice = productService.calculatePrice(
                storeId,
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCustomSize(),
                command.getCondiments());
        boolean flashSale = command.getFlashSaleClaimNo() != null && !command.getFlashSaleClaimNo().isBlank();
        if (flashSale && command.getCouponCode() != null && !command.getCouponCode().isBlank()) {
            throw new ServiceException(400, "秒杀价不能与优惠券叠加");
        }
        double unitPrice = flashSale ? flashSaleService.consumePrice(command.getFlashSaleClaimNo(), product.getCode(), userId, guestId) : regularUnitPrice;
        String fullName = productService.getDisplayName(
                storeId,
                command.getProductCode(),
                command.getSize() != null ? command.getSize() : "MEDIUM",
                command.getCustomSize(),
                command.getCondiments());

        String size = command.getSize() != null ? command.getSize() : "MEDIUM";
        String customSize = command.getCustomSize();
        String condiments = command.getCondiments() != null ? String.join(",", command.getCondiments()) : "";

        double discountRate = 1.0;
        String strategyName = "游客 (无折扣)";
        String memberLevel = "游客";
        double memberDiscount = 0;

        if (!flashSale && userId != null && userId > 0) {
            MemberLevelDTO levelDTO = memberService.getMemberLevel(userId);
            discountRate = levelDTO.getDiscountRate();
            strategyName = levelDTO.getLabel() + " (累计消费¥" + levelDTO.getTotalSpent() + ")";
            memberLevel = levelDTO.getLabel();
            memberDiscount = MoneyUtils.round2(unitPrice * (1 - discountRate));
        }

        if (flashSale) { strategyName = "限时秒杀"; memberLevel = "秒杀专享"; }
        double afterMember = MoneyUtils.round2(unitPrice * discountRate);
        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember, userId);

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
        order.setVoucherNo(coupon.voucherNo);
        order.setTotalCups(1);
        order.setOrderNo(nextOrderNo(command.getStoreId(), product.getCategoryId()));
        // 订单明细（order_item）：单件订单一条，quantity=1，折后价
        OrderItem item = OrderItem.create(product.getCode(), fullName, product.getCategoryCode(),
                size, command.getCondiments(), 1, coupon.finalPrice, null);
        item.setProductId(product.getId());
        item.setOriginalUnitPrice(regularUnitPrice);
        order.setItems(List.of(item));

        inventoryService.reserve(command.getStoreId(), product.getId(), 1);

        order = saveWithRetry(order, product.getCategoryId());
        consumeVoucherIfNeeded(userId, coupon);
        eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));

        // 消费累计延后到订单完成时（COMPLETED）执行，取消/未完成的订单不计入累计消费

        return buildResponse(order, product.getCategoryCode(), coupon, readyTimeStr,
                regularUnitPrice, memberDiscount, 0, "下单成功，请完成支付");
    }

    private OrderResponse createBatchOrder(CreateOrderCommand command) {
        List<CartItemCommand> cartItems = command.getItems();
        Long userId = command.getUserId();
        String guestId = command.getGuestId() != null ? command.getGuestId() : "anonymous";

        // 第一遍：解析每个购物车行（原价单价/商品名/数量）并计算原价总额
        List<CartLine> lines = new ArrayList<>();
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
            lines.add(new CartLine(product, fullName,
                    item.getSize() != null ? item.getSize() : "MEDIUM",
                    item.getCondiments(), item.getQuantity(), unitPrice, product.getCategoryId()));
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
        CouponResult coupon = applyCoupon(command.getCouponCode(), afterMember, userId);
        double finalTotal = coupon.finalPrice;
        double totalDiscount = MoneyUtils.round2(totalOriginal - finalTotal);

        // 第二遍：按原价金额比例把总折扣分摊到每个购物车行（尾差归最后一行），
        // 构造订单明细（order_item 行级：quantity = 购物车数量），行小计合计 = 实付总额
        double allocated = 0;
        int cups = 0;
        List<OrderItem> items = new ArrayList<>();
        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            CartLine line = lines.get(i);
            double lineTotal = MoneyUtils.round2(line.unitPrice * line.quantity);
            double lineDiscount;
            if (i == lines.size() - 1) {
                lineDiscount = MoneyUtils.round2(totalDiscount - allocated);
            } else {
                lineDiscount = MoneyUtils.round2(lineTotal / totalOriginal * totalDiscount);
            }
            allocated += lineDiscount;
            double lineFinal = MoneyUtils.round2(lineTotal - lineDiscount);
            double lineUnitPrice = MoneyUtils.round2(lineFinal / line.quantity);

            OrderItem item = OrderItem.create(line.product.getCode(), line.fullName,
                    line.product.getCategoryCode(), line.size, line.condiments,
                    line.quantity, lineUnitPrice, null);
            item.setProductId(line.product.getId());
            item.setOriginalUnitPrice(line.unitPrice);
            item.setSubtotal(lineFinal);
            items.add(item);
            cups += line.quantity;
            if (nameBuilder.length() > 0) {
                nameBuilder.append("、");
            }
            nameBuilder.append(line.product.getName()).append("×").append(line.quantity);

            OrderResponse.OrderItemResponse itemResp = new OrderResponse.OrderItemResponse();
            itemResp.setProductCode(line.product.getCode());
            itemResp.setBeverageName(line.fullName);
            itemResp.setCategoryCode(line.product.getCategoryCode());
            itemResp.setSize(line.size);
            itemResp.setUnitPrice(lineUnitPrice);
            itemResp.setOriginalUnitPrice(line.unitPrice);
            itemResp.setQuantity(line.quantity);
            itemResp.setSubtotal(lineFinal);
            itemResponses.add(itemResp);
        }

        // 单条订单 + N 条明细（order_item）落库，取餐号 = 该订单 id
        // size 传 "MIXED"（批量订单为多规格混合，user_order.size 非空），实际规格以明细为准
        Order order = orderDomainService.createOrder(userId, guestId, nameBuilder.toString(),
                "MIXED", null, "", totalOriginal, finalTotal, null);
        order.setStoreId(command.getStoreId());
        order.setFulfillmentType(command.getFulfillmentType());
        order.setNote(command.getNote());
        order.setCouponName(coupon.name);
        order.setCouponDiscount(coupon.discount);
        order.setVoucherNo(coupon.voucherNo);
        order.setTotalCups(cups);
        order.setItems(items);
        Long firstCategoryId = lines.isEmpty() ? null : lines.get(0).categoryId;
        order.setOrderNo(nextOrderNo(command.getStoreId(), firstCategoryId));
        for (OrderItem item : items) {
            inventoryService.reserve(command.getStoreId(), item.getProductId(), item.getQuantity());
        }
        order = saveWithRetry(order, firstCategoryId);
        consumeVoucherIfNeeded(userId, coupon);
        eventPublisher.publishEvent(OrderDomainEvent.created(order.getId(), order.getBeverageName(), order.getStatus().name()));

        int earnedPoints = 0;
        if (userId != null && userId > 0) {
            // 消费累计延后到订单完成时（COMPLETED）执行，这里只预估本次可得积分用于展示
            earnedPoints = orderDomainService.calculateEarnedPoints(finalTotal);
        }

        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setOrderName(nameBuilder.toString());
        response.setOriginalPrice(MoneyUtils.round2(totalOriginal));
        response.setFinalPrice(finalTotal);
        response.setPricingStrategy(strategyName);
        response.setStatus("待支付");
        response.setMessage("下单成功！共" + cups + "件，请完成支付");
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

    /** 批量下单的购物车行（标准订单模型：1 订单 + N 条 order_item 明细） */
    private record CartLine(MenuItemDTO product, String fullName, String size,
                            List<String> condiments, int quantity, double unitPrice, Long categoryId) {}

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String action, boolean isUserOrder) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        // 用户端/游客端仅允许取消尚未支付的订单；已支付订单必须通过商家售后流程处理，
        // 避免客户端绕过退款、积分与库存等后续业务。
        if (isUserOrder && (action == null || !"cancel".equalsIgnoreCase(action)
                || order.getStatus() != Order.OrderStatus.UNPAID)) {
            throw new ServiceException(400, "仅待支付订单可以由用户取消");
        }
        Order.OrderStatus newStatus = orderDomainService.calculateNextStatus(order.getStatus().name(), action);
        orderRepository.updateStatus(orderId, newStatus);
        if (newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.UNPAID
                && order.getUserId() != null && order.getVoucherNo() != null) {
            membershipService.restoreVoucher(order.getUserId(), order.getVoucherNo());
        }
        if (newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.UNPAID) {
            orderRepository.findItemsByOrderId(orderId).forEach(item ->
                    inventoryService.release(order.getStoreId(), item.getProductId(), item.getQuantity()));
        }
        eventPublisher.publishEvent(OrderDomainEvent.statusChanged(orderId, order.getBeverageName(), newStatus.name()));
        // 消费累计 + 积分入账：仅在订单完成的那一刻计入（状态机单向，只会触发一次），取消/未完成不计入
        if (!asyncMembershipEnabled && newStatus == Order.OrderStatus.COMPLETED && order.getStatus() != Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.addSpending(order.getUserId(), order.getFinalPrice());
            membershipService.addConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }
        // 取消回滚：已完成订单被取消时，累计消费与积分按实付金额扣回（先扣消费，积分按回滚后重算）
        if (!asyncMembershipEnabled && newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.COMPLETED
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
        if (newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.UNPAID) {
            orderRepository.findItemsByOrderId(orderId).forEach(item ->
                    inventoryService.release(order.getStoreId(), item.getProductId(), item.getQuantity()));
        }
        eventPublisher.publishEvent(OrderDomainEvent.statusChanged(orderId, order.getBeverageName(), newStatus.name()));
        // 消费累计 + 积分入账：仅在订单完成的那一刻计入（状态机单向，只会触发一次），取消/未完成不计入
        if (!asyncMembershipEnabled && newStatus == Order.OrderStatus.COMPLETED && order.getStatus() != Order.OrderStatus.COMPLETED
                && order.getUserId() != null && order.getUserId() > 0) {
            memberService.addSpending(order.getUserId(), order.getFinalPrice());
            membershipService.addConsumptionPoints(order.getUserId(), order.getFinalPrice());
        }
        // 取消回滚：已完成订单被取消时，累计消费与积分按实付金额扣回（先扣消费，积分按回滚后重算）
        if (!asyncMembershipEnabled && newStatus == Order.OrderStatus.CANCELED && order.getStatus() == Order.OrderStatus.COMPLETED
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
        return stats;
    }

    @Override
    public List<Map<String, Object>> getSalesStats(Long storeId, String range) {
        String r = range == null ? "" : range.trim().toLowerCase();
        switch (r) {
            case "14d": return orderRepository.salesDaily(storeId, 14);
            case "28d": return orderRepository.salesDaily(storeId, 28);
            case "12w": return orderRepository.salesWeekly(storeId, 12);
            case "7d":
            default: return orderRepository.salesDaily(storeId, 7);
        }
    }

    @Override
    public List<Map<String, Object>> getHotProducts(Long storeId) {
        return orderRepository.hotProducts(storeId, 7, 5);
    }

    /**
     * 生成详细订单号：YYMMDD-商家6位-类目3位-店铺当日顺序3位（如 260806-687257-007-001）。
     * 顺序号 = 店铺当日单号（当日该店第 N 单，跨分类连续）；商家段为空时回退店铺 id 补 6 位。
     */
    private String nextOrderNo(Long storeId, Long categoryId) {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String merchantPart = merchantPart(storeId);
        String categoryPart = (categoryId != null && categoryId > 0) ? String.format("%03d", categoryId) : "000";
        int seq = orderRepository.maxSeqOfDay(storeId, datePrefix) + 1;
        return String.format("%s-%s-%s-%03d", datePrefix, merchantPart, categoryPart, seq);
    }

    /** 商家段：商家编号去 sj- 前缀取 6 位；无商家/查询失败回退店铺 id 补 6 位 */
    private String merchantPart(Long storeId) {
        try {
            StoreResponse store = storeService.getStore(storeId);
            if (store != null && store.getMerchantId() != null) {
                MerchantResponse merchant = merchantService.getMerchant(store.getMerchantId());
                if (merchant != null && merchant.getMerchantNo() != null
                        && merchant.getMerchantNo().startsWith("sj-")) {
                    return merchant.getMerchantNo().substring(3);
                }
            }
        } catch (Exception ignored) {
            // 商家查询失败不阻塞下单，回退店铺 id
        }
        return String.format("%06d", storeId);
    }

    /** 保存订单；唯一索引冲突（并发同号）时重算订单号重试，最多 3 次 */
    private Order saveWithRetry(Order order, Long categoryId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return orderRepository.save(order);
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw e;
                }
                order.setOrderNo(nextOrderNo(order.getStoreId(), categoryId));
            }
        }
        throw new IllegalStateException("订单保存失败");
    }

    private OrderResponse buildResponse(Order order, String categoryCode, CouponResult coupon,
                                       String readyTimeStr, double original, double memberDiscount,
                                       int earnedPoints, String message) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setOrderName(order.getBeverageName());
        response.setOriginalPrice(MoneyUtils.round2(original));
        response.setFinalPrice(coupon.finalPrice);
        response.setPricingStrategy(coupon.name.isEmpty() ? "游客 (无折扣)" : coupon.name);
        response.setStatus(order.getStatus().getDescription());
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
        m.put("orderNo", order.getOrderNo());
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
        m.put("items", orderRepository.findItemsByOrderId(order.getId()).stream().map(item -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("productId", item.getProductId());
            im.put("beverageName", item.getBeverageName());
            im.put("quantity", item.getQuantity());
            im.put("unitPrice", item.getUnitPrice());
            im.put("originalUnitPrice", item.getOriginalUnitPrice());
            im.put("subtotal", item.getSubtotal());
            return im;
        }).toList());
        return m;
    }

    private CouponResult applyCoupon(String code, double amount, Long userId) {
        if (userId == null || userId <= 0 || code == null || code.isBlank()) return new CouponResult(amount, 0, "", null);
        return switch (code.trim().toUpperCase()) {
            case "FIKA8" -> fixedCoupon(amount, 48, 8, "下午茶立减 ¥8");
            case "SWEET12" -> fixedCoupon(amount, 78, 12, "甜品满 ¥78 减 ¥12");
            case "BEAN15" -> fixedCoupon(amount, 88, 15, "咖啡满 ¥88 减 ¥15");
            default -> voucherCoupon(userId, code.trim(), amount);
        };
    }

    private CouponResult fixedCoupon(double amount, double minimum, double discount, String name) {
        if (amount + 1e-9 < minimum) return new CouponResult(amount, 0, "", null);
        return new CouponResult(MoneyUtils.round2(Math.max(0, amount - discount)), discount, name, null);
    }

    private CouponResult voucherCoupon(Long userId, String voucherNo, double amount) {
        com.coffee.module.membership.api.dto.VoucherDTO voucher = membershipService.validateVoucher(userId, voucherNo, amount);
        double discount = Math.min(amount, voucher.getDiscount() == null ? 0 : voucher.getDiscount());
        return new CouponResult(MoneyUtils.round2(amount - discount), discount, voucher.getName(), voucher.getVoucherNo());
    }

    private void consumeVoucherIfNeeded(Long userId, CouponResult coupon) {
        if (coupon.voucherNo != null) membershipService.consumeVoucher(userId, coupon.voucherNo);
    }

    private record CouponResult(double finalPrice, double discount, String name, String voucherNo) {}
}
