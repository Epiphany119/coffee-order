package com.coffee.order.domain.order.aggregate;

import com.coffee.order.domain.order.entity.OrderStatus;
import com.coffee.order.domain.order.valueobject.Money;
import com.coffee.order.domain.order.valueobject.OrderIdentity;
import com.coffee.order.domain.order.valueobject.OrderLineItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合根
 */
public class OrderAggregate {
    private Long id;
    private final OrderIdentity identity;
    private final List<OrderLineItem> lineItems;
    private final Money money;
    private OrderStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime estimatedReadyTime;
    private String strategyName;
    private String memberLevel;
    private int earnedPoints;
    private double memberDiscount;
    private String couponName;
    private double couponDiscount;

    private OrderAggregate(OrderIdentity identity, List<OrderLineItem> lineItems, Money money) {
        this.identity = identity;
        this.lineItems = new ArrayList<>(lineItems);
        this.money = money;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.estimatedReadyTime = createdAt.plusMinutes(12);
        this.memberLevel = "游客";
        this.earnedPoints = 0;
    }

    public static OrderAggregate createForUser(Long userId, List<OrderLineItem> lineItems, Money money) {
        return new OrderAggregate(OrderIdentity.forUser(userId), lineItems, money);
    }

    public static OrderAggregate createForGuest(String guestId, List<OrderLineItem> lineItems, Money money) {
        return new OrderAggregate(OrderIdentity.forGuest(guestId), lineItems, money);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OrderIdentity getIdentity() {
        return identity;
    }

    public List<OrderLineItem> getLineItems() {
        return List.copyOf(lineItems);
    }

    public Money getMoney() {
        return money;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getEstimatedReadyTime() {
        return estimatedReadyTime;
    }

    public void setEstimatedReadyTime(LocalDateTime estimatedReadyTime) {
        this.estimatedReadyTime = estimatedReadyTime;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(String memberLevel) {
        this.memberLevel = memberLevel;
    }

    public int getEarnedPoints() {
        return earnedPoints;
    }

    public void setEarnedPoints(int earnedPoints) {
        this.earnedPoints = earnedPoints;
    }

    public double getMemberDiscount() {
        return memberDiscount;
    }

    public void setMemberDiscount(double memberDiscount) {
        this.memberDiscount = memberDiscount;
    }

    public String getCouponName() {
        return couponName;
    }

    public void setCouponName(String couponName) {
        this.couponName = couponName;
    }

    public double getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(double couponDiscount) {
        this.couponDiscount = couponDiscount;
    }

    public int getTotalCups() {
        return lineItems.stream().mapToInt(OrderLineItem::quantity).sum();
    }

    public String getBeverageName() {
        if (lineItems.isEmpty()) return "";
        if (lineItems.size() == 1) return lineItems.get(0).beverageName();
        return "多品类订单";
    }

    public String getCondiments() {
        if (lineItems.isEmpty()) return "";
        return lineItems.get(0).condiments().stream().reduce((a, b) -> a + "," + b).orElse("");
    }

    public String getSize() {
        if (lineItems.isEmpty()) return "";
        return lineItems.get(0).size();
    }

    public double getOriginalPrice() {
        return lineItems.isEmpty() ? 0 : lineItems.stream().mapToDouble(OrderLineItem::subtotal).sum();
    }

    public double getFinalPrice() {
        return money.finalAmount();
    }

    public void transitionTo(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isUserOrder() {
        return identity.isUserOrder();
    }

    public Long getUserId() {
        return identity.userId();
    }

    public String getGuestId() {
        return identity.guestId();
    }
}
