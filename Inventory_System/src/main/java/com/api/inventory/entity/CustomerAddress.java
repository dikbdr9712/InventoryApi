package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A saved delivery address of a customer (Home, Office, ...), offered again at checkout. One is the default. */
@Entity
@Table(name = "customer_addresses", indexes = @Index(name = "idx_address_user", columnList = "userEmail"))
@Getter
@Setter
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String userEmail;

    /** Home, Office, ... (the customer's own word) */
    @Column(nullable = false, length = 30)
    private String label;

    @Column(nullable = false, length = 20)
    private String phone;

    /** House, building, landmark. */
    @Column(nullable = false, length = 300)
    private String address;

    /** Where on the map: a delivery area, or a phone location (latitude, longitude). Both empty = not set. */
    private Long areaId;
    private Double latitude;
    private Double longitude;

    /** "Motithang, Thimphu" or "Your current location", as the customer saw it. */
    @Column(length = 120)
    private String pointLabel;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
