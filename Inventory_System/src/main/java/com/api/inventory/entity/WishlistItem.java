package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A product a customer saved for later (the heart). One per customer per product. */
@Entity
@Table(name = "wishlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_user_item", columnNames = {"userEmail", "itemId"}))
@Getter
@Setter
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String userEmail;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Instant createdAt;
}
