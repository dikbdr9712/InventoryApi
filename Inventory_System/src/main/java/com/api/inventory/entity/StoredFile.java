package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An uploaded file kept inside the database (app.files.store=database): a product photo (area "uploads") or a
 * seller's / driver's document (area "partners"). Used where the server has no lasting disk (free hosting).
 */
@Entity
@Table(name = "stored_files", uniqueConstraints = @UniqueConstraint(name = "uk_stored_file", columnNames = {"area", "name"}))
@Getter
@Setter
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String area;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false, columnDefinition = "longblob")
    private byte[] data;

    @Column(nullable = false)
    private Instant createdAt;
}
