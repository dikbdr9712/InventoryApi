package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An identity or business document a seller or driver sent with their application (CID, driving licence,
 * trade licence). The file lives in the PRIVATE upload folder and only admins can open it.
 * A newer document of the same kind replaces the older one for review (the old row is kept).
 */
@Entity
@Table(name = "partner_documents", indexes = @Index(name = "idx_doc_partner", columnList = "partnerType,partnerId"))
@Getter
@Setter
public class PartnerDocument {

    public static final String ID_CARD = "ID_CARD";             // CID (citizenship identity card)
    public static final String DRIVING_LICENCE = "DRIVING_LICENCE";
    public static final String TRADE_LICENCE = "TRADE_LICENCE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SELLER or RIDER. */
    @Column(nullable = false, length = 10)
    private String partnerType;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false, length = 30)
    private String kind;

    /** Random name on disk (never the name the person chose). */
    @Column(nullable = false, length = 100)
    private String storedName;

    @Column(length = 200)
    private String originalName;

    @Column(nullable = false, length = 60)
    private String contentType;

    private Long sizeBytes;

    @Column(nullable = false)
    private Instant uploadedAt;

    /** False once a newer document of the same kind was sent. */
    @Column(nullable = false)
    private Boolean current = true;
}
