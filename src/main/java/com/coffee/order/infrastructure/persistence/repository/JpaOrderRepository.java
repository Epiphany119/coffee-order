package com.coffee.order.infrastructure.persistence.repository;

import com.coffee.order.domain.order.aggregate.OrderAggregate;
import com.coffee.order.domain.order.entity.OrderStatus;
import com.coffee.order.domain.order.repository.OrderRepository;
import com.coffee.order.domain.order.valueobject.OrderLineItem;
import com.coffee.order.domain.order.valueobject.Money;
import com.coffee.order.infrastructure.persistence.entity.UserOrderEntity;
import com.coffee.order.infrastructure.persistence.entity.GuestOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JPA 订单仓储实现
 */
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final UserOrderJpaRepository userOrderJpaRepository;
    private final GuestOrderJpaRepository guestOrderJpaRepository;

    public JpaOrderRepository(UserOrderJpaRepository userOrderJpaRepository,
                              GuestOrderJpaRepository guestOrderJpaRepository) {
        this.userOrderJpaRepository = userOrderJpaRepository;
        this.guestOrderJpaRepository = guestOrderJpaRepository;
    }

    @Override
    public OrderAggregate save(OrderAggregate order) {
        if (order.isUserOrder()) {
            UserOrderEntity entity = toUserEntity(order);
            entity = userOrderJpaRepository.save(entity);
            order.setId(entity.getId());
        } else {
            GuestOrderEntity entity = toGuestEntity(order);
            entity = guestOrderJpaRepository.save(entity);
            order.setId(entity.getId());
        }
        return order;
    }

    @Override
    public Optional<OrderAggregate> findById(Long id) {
        return userOrderJpaRepository.findById(id)
                .map(UserOrderEntity::toDomain)
                .or(() -> guestOrderJpaRepository.findById(id)
                        .map(this::guestToDomain));
    }

    @Override
    public List<OrderAggregate> findByUserId(Long userId) {
        return userOrderJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(UserOrderEntity::toDomain)
                .toList();
    }

    @Override
    public List<OrderAggregate> findByGuestId(String guestId) {
        return guestOrderJpaRepository.findByGuestIdOrderByCreatedAtDesc(guestId)
                .stream()
                .map(this::guestToDomain)
                .toList();
    }

    @Override
    public List<OrderAggregate> findByStatus(OrderStatus status) {
        List<OrderAggregate> results = new java.util.ArrayList<>();
        userOrderJpaRepository.findAll().stream()
                .filter(e -> e.getStatus().equals(status.name()))
                .map(UserOrderEntity::toDomain)
                .forEach(results::add);
        return results;
    }

    @Override
    public List<OrderAggregate> findAll() {
        List<OrderAggregate> results = new java.util.ArrayList<>();
        userOrderJpaRepository.findAllByOrderByCreatedAtDesc()
                .forEach(e -> results.add(e.toDomain()));
        guestOrderJpaRepository.findAllByOrderByCreatedAtDesc()
                .forEach(e -> results.add(guestToDomain(e)));
        return results;
    }

    @Override
    public void updateStatus(Long orderId, OrderStatus status) {
        userOrderJpaRepository.findById(orderId).ifPresent(e -> {
            e.setStatus(status.name());
            userOrderJpaRepository.save(e);
        });
        guestOrderJpaRepository.findById(orderId).ifPresent(e -> {
            e.setStatus(status.name());
            guestOrderJpaRepository.save(e);
        });
    }

    @Override
    public List<OrderAggregate> findReadyBefore(LocalDateTime time) {
        List<OrderAggregate> results = new java.util.ArrayList<>();
        userOrderJpaRepository.findByEstimatedReadyTimeBeforeAndStatus(time, "PENDING")
                .forEach(e -> results.add(e.toDomain()));
        return results;
    }

    private OrderAggregate guestToDomain(GuestOrderEntity entity) {
        List<String> condimentList = entity.getCondiments() != null && !entity.getCondiments().isBlank()
                ? List.of(entity.getCondiments().split(","))
                : List.of();

        OrderLineItem lineItem = OrderLineItem.create(
                "", entity.getBeverageName(), "", entity.getSize(),
                condimentList, 1, entity.getOriginalPrice(), entity.getEstimatedReadyTime());

        OrderAggregate order = OrderAggregate.createForGuest(entity.getGuestId(), List.of(lineItem),
                Money.withDiscount(entity.getOriginalPrice(), entity.getOriginalPrice() - entity.getFinalPrice()));
        order.setId(entity.getId());
        order.transitionTo(OrderStatus.valueOf(entity.getStatus()));
        return order;
    }

    private UserOrderEntity toUserEntity(OrderAggregate order) {
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

    private GuestOrderEntity toGuestEntity(OrderAggregate order) {
        GuestOrderEntity entity = new GuestOrderEntity();
        entity.setId(order.getId());
        entity.setGuestId(order.getGuestId());
        entity.setBeverageName(order.getBeverageName());
        entity.setSize(order.getSize());
        entity.setCondiments(order.getCondiments());
        entity.setOriginalPrice(order.getOriginalPrice());
        entity.setFinalPrice(order.getFinalPrice());
        entity.setStatus(order.getStatus().name());
        entity.setEstimatedReadyTime(order.getEstimatedReadyTime());
        return entity;
    }

    public interface UserOrderJpaRepository extends JpaRepository<UserOrderEntity, Long> {
        List<UserOrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
        List<UserOrderEntity> findAllByOrderByCreatedAtDesc();
        List<UserOrderEntity> findByEstimatedReadyTimeBeforeAndStatus(LocalDateTime time, String status);
    }

    public interface GuestOrderJpaRepository extends JpaRepository<GuestOrderEntity, Long> {
        List<GuestOrderEntity> findByGuestIdOrderByCreatedAtDesc(String guestId);
        List<GuestOrderEntity> findAllByOrderByCreatedAtDesc();
    }
}
