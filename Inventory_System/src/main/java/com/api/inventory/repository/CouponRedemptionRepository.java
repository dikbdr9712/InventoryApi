package com.api.inventory.repository;

import com.api.inventory.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    /** Uses that count: orders that were not cancelled. */
    @Query(value = "SELECT COUNT(*) FROM coupon_redemptions r JOIN orders o ON o.order_id = r.order_id "
            + "WHERE r.coupon_id = ?1 AND UPPER(o.order_status) <> 'CANCELLED'", nativeQuery = true)
    long countUses(Long couponId);

    @Query(value = "SELECT COUNT(*) FROM coupon_redemptions r JOIN orders o ON o.order_id = r.order_id "
            + "WHERE r.coupon_id = ?1 AND LOWER(r.user_email) = LOWER(?2) AND UPPER(o.order_status) <> 'CANCELLED'", nativeQuery = true)
    long countUsesBy(Long couponId, String email);

    long countByCouponId(Long couponId);
}
