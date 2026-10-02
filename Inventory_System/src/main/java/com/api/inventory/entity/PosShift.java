package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One cashier's time at the till, from opening the cash drawer to counting it at the end.
 *
 *   expected cash = opening float + cash sales - cash refunds
 *   difference    = counted cash - expected cash   (negative = money missing)
 *
 * Every counter sale belongs to the shift that was open when it was made. A cashier has at most one open shift.
 */
@Entity
@Table(name = "pos_shifts", indexes = @Index(name = "idx_shift_cashier", columnList = "cashierEmail,status"))
@Getter
@Setter
public class PosShift {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String cashierEmail;

    @Column(length = 120)
    private String cashierName;

    @Column(nullable = false, length = 10)
    private String status = OPEN;

    @Column(nullable = false)
    private Instant openedAt;

    /** Cash put in the drawer at the start (change for customers). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingFloat = BigDecimal.ZERO;

    private Instant closedAt;

    @Column(length = 150)
    private String closedBy;

    // ----- filled in when the drawer is closed (frozen, so the report never changes afterwards) -----

    @Column(precision = 12, scale = 2)
    private BigDecimal cashSales;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherSales;

    @Column(precision = 12, scale = 2)
    private BigDecimal cashRefunds;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal difference;

    private Integer saleCount;

    @Column(length = 500)
    private String closingNote;
}
