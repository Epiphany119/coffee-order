package com.coffee.order.infrastructure.persistence.entity;

import com.coffee.order.domain.order.entity.OrderStatus;
import com.coffee.order.domain.order.valueobject.OrderIdentity;
import com.coffee.order.domain.order.valueobject.OrderLineItem;
import com.coffee.order.domain.order.valueobject.Money;
import com.coffee.order.domain.order.aggregate.OrderAggregate;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 JPA 实体
 */
@Entity
@Table(name = "user_order")
public class UserOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String beverageName;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(length = 200)
    private String condiments;

    @Column(nullable = false)
    private double originalPrice;

    @Column(nullable = false)
    private double finalPrice;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime estimatedReadyTime;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBeverageName() { return beverageName; }
    public void setBeverageName(String beverageName) { this.beverageName = beverageName; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getCondiments() { return condiments; }
    public void setCondiments(String condiments) { this.condiments = condiments; }
    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public double getFinalPrice() { return finalPrice; }
    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getEstimatedReadyTime() { return estimatedReadyTime; }
    public void setEstimatedReadyTime(LocalDateTime estimatedReadyTime) { this.estimatedReadyTime = estimatedReadyTime; }

    public OrderAggregate toDomain() {
        List<String> condimentList = condiments != null && !condiments.isBlank()
                ? List.of(condiments.split(","))
                : List.of();

        OrderLineItem lineItem = OrderLineItem.create(
                "", beverageName, "", size,
                condimentList, 1, originalPrice, estimatedReadyTime);

        OrderAggregate order = OrderAggregate.createForUser(userId, List.of(lineItem),
                Money.withDiscount(originalPrice, originalPrice - finalPrice));
        order.setId(id);
        order.transitionTo(OrderStatus.valueOf(status));
        return order;
    }

    public static UserOrderEntity fromDomain(OrderAggregate order) {
        UserOrderEntity entity = new UserOrderEntity();
        entity.setId(order.getId());
        entity.setUserId(order.getUserId());
        entity.setBeverageName(order.getBeverageName());
        entity.setSize(order.getSize());
        entity.setCondiments(order.getCondiments());
        entity.setOriginalPrice(order.getOriginalPrice());
        entity.setFinalPrice(order.getFinalPrice());
        entity.setStatus(order.getStatus().name());
        entity.setEstimatedReadyTime(order.getEstimatedReadyTime());
        return entity;
    }
}
