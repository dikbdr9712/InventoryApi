package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A "choose a new password" link sent by email. Only a fingerprint (SHA-256) of the secret part of the link is
 * stored, so even someone who reads the database cannot use a link. Works once, for 30 minutes.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_reset_user", columnList = "userId"),
        @Index(name = "idx_reset_created", columnList = "createdAt")
})
@Getter
@Setter
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Used, or replaced by a newer link. */
    private Instant usedAt;

    @Column(length = 64)
    private String requestIp;
}
