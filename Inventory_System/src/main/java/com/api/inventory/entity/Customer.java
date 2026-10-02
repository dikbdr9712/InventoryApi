package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A person who buys from us: online (with an account) or at the counter (a walk-in who gave a phone number).
 * Orders point to their customer (orders.customer_id), so a customer's whole history is in one place.
 *
 * The same person is recognised by: their account first, then email, then phone number.
 * Totals (visits, money spent) are worked out from the orders, never stored twice.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_phone", columnList = "phone"),
        @Index(name = "idx_customer_email", columnList = "email"),
        @Index(name = "idx_customer_user", columnList = "userId")
})
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 500)
    private String address;

    /** Their online account, when they have one. */
    private Long userId;

    /** Staff notes, e.g. "prefers delivery after 5 pm". Never shown to the customer. */
    @Column(length = 1000)
    private String notes;

    /** Where we first met them: ONLINE, POS or SIGNUP. */
    @Column(length = 10)
    private String firstSource;

    private Instant createdAt;
    private Instant lastSeenAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = createdAt;
        }
    }
}
