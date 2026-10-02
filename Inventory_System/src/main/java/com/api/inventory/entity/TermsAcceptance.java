package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Proof that a person agreed to a version of an agreement: who, which version, when, and from where.
 * Rows are only ever added (never changed), like a signed paper in a file.
 */
@Entity
@Table(name = "terms_acceptances", indexes = @Index(name = "idx_terms_user", columnList = "userEmail,termsType"))
@Getter
@Setter
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, length = 150)
    private String userEmail;

    @Column(nullable = false, length = 20)
    private String termsType;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private Instant acceptedAt;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 300)
    private String userAgent;
}
