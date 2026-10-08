package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** An order used a coupon. A cancelled order's use does not count against the limits. */
@Entity
@Table(name = "coupon_redemptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_redemption_order", columnNames = "orderId"),
        indexes = @Index(name = "idx_coupon_redemption_coupon", columnList = "couponId, userEmail"))
@Getter
@Setter
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 120)
    private String userEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt;
}
