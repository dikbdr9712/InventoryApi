package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The marketplace rules an admin can change. There is exactly one row (id = 1).
 * Changing them affects NEW orders only: every package keeps the numbers it was created with.
 */
@Entity
@Table(name = "marketplace_settings")
@Getter
@Setter
public class MarketplaceSettings {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    /** Our commission on a seller's sales (percent of the selling price, before GST), unless the seller has their own rate. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultCommissionPercent = new BigDecimal("3.00");

    /** OLD flat fee per package, no longer used (delivery is priced by size and distance below). Kept so old databases stay valid. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    /** OLD flat rider pay, no longer used (the rider gets riderSharePercent of each delivery fee). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal riderPayPerDelivery = new BigDecimal("50.00");

    // ---------- Delivery rates: fee = base fee + (km beyond the included km) x per-km rate, for the package's size ----------
    // Empty columns (databases from before these existed) use the defaults in DeliveryPricingService.

    @Column(precision = 10, scale = 2)
    private BigDecimal smallBaseFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal smallPerKm;
    @Column(precision = 10, scale = 2)
    private BigDecimal mediumBaseFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal mediumPerKm;
    @Column(precision = 10, scale = 2)
    private BigDecimal largeBaseFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal largePerKm;
    @Column(precision = 10, scale = 2)
    private BigDecimal bulkyBaseFee;
    @Column(precision = 10, scale = 2)
    private BigDecimal bulkyPerKm;

    /** Km covered by the base fee. */
    @Column(precision = 5, scale = 1)
    private BigDecimal includedKm;

    /** The rider's part of each delivery fee, in percent. The rest covers our costs. */
    @Column(precision = 5, scale = 2)
    private BigDecimal riderSharePercent;

    /** We do not deliver further than this (road km from the seller). */
    @Column(precision = 6, scale = 1)
    private BigDecimal maxDistanceKm;

    /** Used when the customer gives no location: the fee is worked out for this many km and marked as estimated. */
    @Column(precision = 5, scale = 1)
    private BigDecimal unknownDistanceKm;

    /** Where riders pick up our own shop's products. */
    @Column(length = 300)
    private String shopAddress;
    private Double shopLatitude;
    private Double shopLongitude;

    private Instant updatedAt;
    private String updatedBy;
}
