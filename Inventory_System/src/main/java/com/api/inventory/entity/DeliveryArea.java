package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A neighbourhood customers can pick at checkout when they do not share their phone's location
 * (for example "Babesa, Thimphu"). An admin adds them with a map point, and the delivery distance is measured from it.
 */
@Entity
@Table(name = "delivery_areas")
@Getter
@Setter
public class DeliveryArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 80)
    private String town;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private boolean active = true;

    private Instant createdAt;
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
