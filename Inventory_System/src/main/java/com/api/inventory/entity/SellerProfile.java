package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A shopkeeper who sells through our system. One per user.
 * The person applies (PENDING); an admin approves them (APPROVED, and their role becomes SELLER).
 * Money from their sales is paid to the bank account below, minus our commission.
 */
@Entity
@Table(name = "seller_profiles")
@Getter
@Setter
public class SellerProfile {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUSPENDED = "SUSPENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 120)
    private String shopName;

    @Column(length = 20)
    private String phone;

    /** Where riders collect the packages. */
    @Column(nullable = false, length = 500)
    private String pickupAddress;

    @Column(length = 80)
    private String town;

    /** The pickup point on the map, used to measure delivery distance. Empty = not set yet (distance is estimated). */
    private Double pickupLatitude;
    private Double pickupLongitude;

    @Column(length = 500)
    private String description;

    @Column(length = 120)
    private String bankName;

    @Column(length = 120)
    private String bankAccountName;

    @Column(length = 40)
    private String bankAccountNumber;

    /** Citizenship identity card number of the owner (11 digits). */
    @Column(length = 20)
    private String cidNumber;

    /** Trade licence number of the business (if registered). */
    @Column(length = 40)
    private String tradeLicenseNumber;

    /** Taxpayer number (TPN), if the business has one. */
    @Column(length = 30)
    private String tpnNumber;

    /** The Seller Agreement version accepted with the application. */
    private Integer termsVersion;
    private Instant termsAcceptedAt;

    /** Our commission for this seller, in percent. Empty = the default in the marketplace settings. */
    @Column(precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    /** Why an application was refused or an account suspended (shown to the person). */
    @Column(length = 500)
    private String statusNote;

    private Instant createdAt;
    private Instant reviewedAt;
    private String reviewedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isApproved() {
        return APPROVED.equals(status);
    }
}
