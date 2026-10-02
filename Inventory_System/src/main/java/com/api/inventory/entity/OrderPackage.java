package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The part of an online order that one seller sends, carried by one rider.
 * An order with items from two sellers has two packages. Items from our own shop form a package with no seller.
 *
 *   PENDING_PAYMENT -> TO_PACK -> READY_FOR_PICKUP -> ASSIGNED -> PICKED_UP -> DELIVERED
 *   (any step before PICKED_UP can become CANCELLED)
 *
 * The money numbers are frozen when the package is created, so later changes to rates never rewrite old orders.
 */
@Entity
@Table(name = "order_packages", indexes = {
        @Index(name = "idx_pkg_order", columnList = "orderId"),
        @Index(name = "idx_pkg_seller", columnList = "sellerId"),
        @Index(name = "idx_pkg_rider", columnList = "riderId"),
        @Index(name = "idx_pkg_status", columnList = "status")
})
@Getter
@Setter
public class OrderPackage {

    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String TO_PACK = "TO_PACK";
    public static final String READY_FOR_PICKUP = "READY_FOR_PICKUP";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    /** SellerProfile id, or empty for our own shop. */
    private Long sellerId;

    /** RiderProfile id once a rider takes the job. Empty when staff deliver it themselves. */
    private Long riderId;

    @Column(nullable = false, length = 20)
    private String status = PENDING_PAYMENT;

    /** Sum of the item lines (selling price x quantity), before GST. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsSubtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    /** What we keep: itemsSubtotal x commissionPercent. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /** What the seller gets: itemsSubtotal - commissionAmount. 0 for our own shop. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellerEarning = BigDecimal.ZERO;

    /** What the customer paid for delivering this package. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    /** What the rider earns for delivering it. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal riderPay = BigDecimal.ZERO;

    /** Size of the biggest product in it (DeliverySize). Decides the rate and which riders see the job. */
    @Column(length = 10)
    private String deliverySize;

    /** Road distance from pickup to drop, in km. */
    @Column(precision = 6, scale = 1)
    private BigDecimal distanceKm;

    /** True when a map point was missing and the fee used the standard distance instead. */
    private Boolean distanceEstimated;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;

    /** Copied when the package is made, so the rider still knows where to go if the seller later moves. */
    @Column(length = 500)
    private String pickupAddress;

    @Column(length = 500)
    private String dropAddress;

    /** 4 digits the customer gives the rider at the door. Proves the right person received it. */
    @Column(length = 6)
    private String deliveryCode;

    private Instant createdAt;
    private Instant packedAt;
    private Instant assignedAt;
    private Instant pickedUpAt;
    private Instant deliveredAt;
    private Instant cancelledAt;

    /** Who did the last step (email), for the record. */
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
