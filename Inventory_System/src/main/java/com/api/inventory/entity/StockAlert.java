package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** "Notify me" on a sold-out product. notifiedAt is set when the customer was told it is back. */
@Entity
@Table(name = "stock_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_alert_user_item", columnNames = {"userEmail", "itemId"}),
        indexes = @Index(name = "idx_stock_alert_item", columnList = "itemId, notifiedAt"))
@Getter
@Setter
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String userEmail;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Instant createdAt;

    /** When the customer was told it is back (empty = still waiting). */
    private Instant notifiedAt;
}
