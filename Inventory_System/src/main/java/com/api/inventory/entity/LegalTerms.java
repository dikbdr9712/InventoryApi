package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One version of an agreement people must accept: the Seller Agreement, the Driver Agreement,
 * or the customers' Terms of Use and Privacy notice.
 *
 * Versions are never edited after publishing: a change is a NEW version, so we can always show
 * exactly what a person agreed to. The highest version of a type is the current one.
 */
@Entity
@Table(name = "legal_terms", uniqueConstraints = @UniqueConstraint(columnNames = {"termsType", "version"}))
@Getter
@Setter
public class LegalTerms {

    public static final String SELLER = "SELLER";
    public static final String RIDER = "RIDER";
    public static final String CUSTOMER = "CUSTOMER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String termsType;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 150)
    private String title;

    /** Plain text: "# " heading, "## " sub-heading, "- " list item, blank line = new paragraph. */
    // The length matters: without it MySQL gets TINYTEXT (255 characters). 1,000,000 gives MEDIUMTEXT (16 MB).
    @Lob
    @Column(nullable = false, length = 1_000_000)
    private String body;

    /** What changed compared with the previous version (shown to people asked to accept again). */
    @Column(length = 500)
    private String changeSummary;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(length = 150)
    private String publishedBy;
}
