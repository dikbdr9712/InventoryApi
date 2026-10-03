package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One message in a person's notification list (the bell at the top of the site), for example
 * "Order #16 is on the way". Emails and text messages about the same thing are sent alongside.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "userEmail, createdAt"),
        @Index(name = "idx_notif_unread", columnList = "userEmail, readAt")
})
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String userEmail;

    /** What happened, for example ORDER_PAID, NEW_JOB, PAYOUT. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String body;

    /** Where clicking it goes in the site, for example /orders/16. */
    @Column(length = 200)
    private String link;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;
}
