package com.api.inventory.repository;

import com.api.inventory.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    /** Every return made against one sale, newest first */
    List<SalesReturn> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<SalesReturn> findByCreatedByAndCreatedAtBetween(String createdBy, java.time.Instant from, java.time.Instant to);
}