package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The money book for sellers and riders. A positive amount is money we owe them; a payout is negative.
 * Balance owed = the sum of their entries. Entries are never edited or deleted, only added.
 *
 *   SALE       seller earned this from a delivered package (sale minus commission)
 *   DELIVERY   rider earned this for delivering a package
 *   PAYOUT     we paid them (negative)
 *   ADJUSTMENT a correction by an admin (either sign), always with a note
 *   RETURN     a customer returned a seller's items: the seller's share of them comes back (negative)
 */
@Entity
@Table(name = "earnings_ledger", indexes = {
        @Index(name = "idx_ledger_party", columnList = "partyType,partyId")
})
@Getter
@Setter
public class LedgerEntry {

    public static final String SELLER = "SELLER";
    public static final String RIDER = "RIDER";

    public static final String SALE = "SALE";
    public static final String DELIVERY = "DELIVERY";
    public static final String PAYOUT = "PAYOUT";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String RETURN = "RETURN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String partyType;

    /** SellerProfile id or RiderProfile id. */
    @Column(nullable = false)
    private Long partyId;

    @Column(nullable = false, length = 20)
    private String entryType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private Long orderId;
    private Long packageId;

    /** Bank journal number of a payout, or the reason for an adjustment. */
    @Column(length = 300)
    private String note;

    private Instant createdAt;
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
