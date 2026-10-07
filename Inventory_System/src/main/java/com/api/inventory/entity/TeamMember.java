package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A person on the About page's "Meet the team". */
@Entity
@Table(name = "team_members", indexes = @Index(name = "idx_team_order", columnList = "sortOrder"))
@Getter
@Setter
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 80)
    private String role;

    /** A sentence or two about them. */
    @Column(length = 400)
    private String bio;

    /** "/uploads/team-3-ab12cd34.jpg" (uploaded), or a picture of the website ("Images/..."); empty = initials. */
    @Column(length = 200)
    private String photo;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;
}
