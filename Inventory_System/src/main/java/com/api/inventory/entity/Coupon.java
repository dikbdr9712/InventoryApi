package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A coupon code: a percent (PERCENT) or an amount (AMOUNT) off the items of an online order, never the delivery.
 * DP DrukBazaars pays for it, so sellers earn their full share.
 */
@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"))
@Getter
@Setter
public class Coupon {

    public static final String PERCENT = "PERCENT";
    public static final String AMOUNT = "AMOUNT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Upper case, letters and digits: DRUK10 */
    @Column(nullable = false, length = 30)
    private String code;

    /** For staff and the customer: "10% off for Losar". */
    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 10)
    private String kind;

    /** 10 (percent) or 100.00 (Nu.) */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /** The items must come to at least this. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrder = BigDecimal.ZERO;

    /** The most a percent coupon takes off (empty = no limit). */
    @Column(precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    private Instant startsAt;
    private Instant endsAt;

    /** How many orders in all may use it (empty = no limit). */
    private Integer usageLimit;

    /** How many orders one customer may use it for. */
    @Column(nullable = false)
    private int perCustomerLimit = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 120)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}
