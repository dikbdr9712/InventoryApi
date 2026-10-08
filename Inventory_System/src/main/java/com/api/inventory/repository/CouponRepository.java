package com.api.inventory.repository;

import com.api.inventory.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    /** Locked while an order uses it, so two orders cannot both take the last use. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where upper(c.code) = upper(?1)")
    Optional<Coupon> findByCodeForUpdate(String code);

    List<Coupon> findAllByOrderByCreatedAtDesc();
}
