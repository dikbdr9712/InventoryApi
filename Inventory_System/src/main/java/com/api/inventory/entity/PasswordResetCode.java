package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A 6-digit "forgot password" code sent by email or text message. Only a BCrypt hash of the code is stored.
 * Works for 10 minutes and 5 tries; the right code gives a one-time ticket to choose a new password.
 */
@Entity
@Table(name = "password_reset_codes", indexes = {
        @Index(name = "idx_reset_code_user", columnList = "userId, channel, createdAt"),
        @Index(name = "idx_reset_code_created", columnList = "createdAt")
})
@Getter
@Setter
public class PasswordResetCode {

    public static final String EMAIL = "EMAIL";
    public static final String SMS = "SMS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** EMAIL or SMS: where the code was sent. */
    @Column(nullable = false, length = 10)
    private String channel;

    @Column(nullable = false, length = 100)
    private String codeHash;

    /** Wrong tries so far; at 5 the code stops working. */
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Used, cancelled by a newer code, or tried too often. */
    private Instant usedAt;

    @Column(length = 64)
    private String requestIp;
}
