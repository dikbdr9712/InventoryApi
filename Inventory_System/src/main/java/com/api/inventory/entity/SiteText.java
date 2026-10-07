package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A text on the website that staff can change (About page: introduction, mission, vision). */
@Entity
@Table(name = "site_texts")
@Getter
@Setter
public class SiteText {

    @Id
    @Column(length = 40)
    private String textKey;

    @Column(length = 2000)
    private String textValue;

    @Column(length = 120)
    private String updatedBy;

    private Instant updatedAt;
}
