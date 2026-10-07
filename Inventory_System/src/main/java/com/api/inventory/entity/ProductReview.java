package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A customer's rating of a product they bought and received (1 to 5 stars, a comment if they like).
 * One per customer per product: rating it again changes it. Only "verified purchases": the product must have been
 * delivered to them in one of their orders. Staff can hide an abusive review and reply under it.
 */
@Entity
@Table(name = "product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_user_item", columnNames = {"userEmail", "itemId"}),
        indexes = @Index(name = "idx_review_item", columnList = "itemId, hidden"))
@Getter
@Setter
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    /** The order in which they received it (the latest one when they rate again). */
    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 120)
    private String userEmail;

    /** Shown publicly: first name and the first letter of the last name ("Karma D."). */
    @Column(nullable = false, length = 60)
    private String displayName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    /** Hidden by staff (abusive, not about the product): not shown and not counted. */
    @Column(nullable = false)
    private boolean hidden;

    /** The shop's public answer. */
    @Column(length = 500)
    private String reply;

    @Column(length = 120)
    private String repliedBy;

    private Instant repliedAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
