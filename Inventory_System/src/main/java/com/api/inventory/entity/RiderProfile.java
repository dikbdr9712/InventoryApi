package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A delivery person. One per user.
 * The person applies (PENDING); an admin approves them (APPROVED, and their role becomes RIDER).
 * They pick packages up from sellers, deliver them, and earn the rider pay set in the marketplace settings.
 */
@Entity
@Table(name = "rider_profiles")
@Getter
@Setter
public class RiderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 20)
    private String phone;

    /** Bike, scooter, car, taxi... */
    @Column(nullable = false, length = 40)
    private String vehicleType;

    @Column(length = 30)
    private String vehicleNumber;

    @Column(length = 40)
    private String licenseNumber;

    /** Citizenship identity card number (11 digits). */
    @Column(length = 20)
    private String cidNumber;

    /** The driving licence stops being valid after this day (empty = no licence, e.g. on foot or bicycle). */
    private java.time.LocalDate licenseExpiry;

    @Column(length = 120)
    private String emergencyContactName;

    @Column(length = 20)
    private String emergencyContactPhone;

    /** The Driver Agreement version accepted with the application. */
    private Integer termsVersion;
    private Instant termsAcceptedAt;

    /** True when the vehicle needs a driving licence (motorbike, scooter, car, taxi). */
    public boolean needsLicence() {
        String v = vehicleType == null ? "" : vehicleType.trim().toLowerCase();
        return !(v.equals("bicycle") || v.equals("on foot"));
    }

    /** A vehicle that needs a licence, and the licence has run out. */
    public boolean licenceExpired() {
        return needsLicence() && licenseExpiry != null && licenseExpiry.isBefore(java.time.LocalDate.now());
    }

    /** The town or area they deliver in. */
    @Column(nullable = false, length = 80)
    private String town;

    @Column(length = 120)
    private String bankName;

    @Column(length = 120)
    private String bankAccountName;

    @Column(length = 40)
    private String bankAccountNumber;

    /** Same values as SellerProfile: PENDING, APPROVED, REJECTED, SUSPENDED. */
    @Column(nullable = false, length = 20)
    private String status = SellerProfile.PENDING;

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
        return SellerProfile.APPROVED.equals(status);
    }
}
