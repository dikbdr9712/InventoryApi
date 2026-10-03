package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One attempt to pay an order online. The customer is sent to the payment gateway with our reference,
 * and the gateway tells us the result. The order is confirmed automatically when it is PAID.
 *
 *   CREATED -> PAID | FAILED | CANCELLED | EXPIRED (not finished within an hour)
 */
@Entity
@Table(name = "payment_intents", indexes = {
        @Index(name = "idx_intent_order", columnList = "orderId"),
        @Index(name = "idx_intent_status", columnList = "status, createdAt")
})
@Getter
@Setter
public class PaymentIntent {

    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Our reference, sent to the gateway and shown to the customer, for example PI-3F9A1C2B7D10. */
    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 120)
    private String customerEmail;

    /** What must be paid: the order's total when the attempt started. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "BTN";

    /** Which gateway, for example SANDBOX. */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 12)
    private String status = CREATED;

    /** The gateway's own transaction number. */
    @Column(length = 80)
    private String providerReference;

    @Column(length = 300)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    public boolean isFinal() {
        return !CREATED.equals(status);
    }
}
