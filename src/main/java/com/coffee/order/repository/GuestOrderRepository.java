package com.coffee.order.repository;

import com.coffee.order.entity.GuestOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GuestOrderRepository extends JpaRepository<GuestOrder, Long> {
    List<GuestOrder> findByGuestIdOrderByCreatedAtDesc(String guestId);
    List<GuestOrder> findAllByOrderByCreatedAtDesc();
}
