package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One more photo of a product, besides its main photo (item_master.image_path). Shown in order. */
@Entity
@Table(name = "item_photos", indexes = @Index(name = "idx_item_photo_item", columnList = "itemId, position"))
@Getter
@Setter
public class ItemPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    /** /uploads/item-12-ab12cd34.jpg */
    @Column(nullable = false, length = 255)
    private String path;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private Instant createdAt;
}
