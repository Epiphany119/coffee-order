package com.coffee.order.repository;

import com.coffee.order.entity.MemberPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberPointsRepository extends JpaRepository<MemberPoints, Long> {
    Optional<MemberPoints> findByUserId(Long userId);
}
