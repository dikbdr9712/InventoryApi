package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Who changed what, for the things that matter: people, roles and permissions, marketplace money, cash drawers.
 * Rows are only ever added.
 */
@Entity
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_at", columnList = "at"))
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    /** Email of the person who did it ("system" for start-up tasks). */
    @Column(nullable = false, length = 150)
    private String actor;

    /** Short code, e.g. USER_ROLE_CHANGED, ROLE_PERMISSIONS_CHANGED, PAYOUT, SHIFT_CLOSED. */
    @Column(nullable = false, length = 50)
    private String action;

    /** What it was done to, e.g. "user 12 (dorji@x.bt)" or "role CASHIER". */
    @Column(length = 200)
    private String target;

    @Column(length = 1000)
    private String details;
}
