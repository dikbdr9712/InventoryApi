package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * How the customer rates the service of one delivered order: the shop (ordering, packing, help) and the delivery
 * (1 to 5 stars each) and a comment. One per order; rating again changes it. The delivery rating counts for the
 * driver who delivered it. Shown on the Customer reviews page unless staff hide it.
 */
@Entity
@Table(name = "order_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_feedback_order", columnNames = "orderId"),
        indexes = @Index(name = "idx_feedback_rider", columnList = "riderId"))
@Getter
@Setter
public class OrderFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 120)
    private String userEmail;

    @Column(nullable = false, length = 60)
    private String displayName;

    /** The shop's service: ordering, packing, answers. */
    @Column(nullable = false)
    private int serviceRating;

    /** The delivery: on time, friendly, the package in good shape. */
    @Column(nullable = false)
    private int deliveryRating;

    @Column(length = 1000)
    private String comment;

    /** The driver who delivered it (empty when our staff did, or for old orders). */
    private Long riderId;

    @Column(nullable = false)
    private boolean hidden;

    @Column(length = 500)
    private String reply;

    @Column(length = 120)
    private String repliedBy;

    private Instant repliedAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
