// src/main/java/com/api/inventory/entity/Role.java
package com.api.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A job in the shop (Manager, Cashier, Seller...). What a role may do is the set of permission keys below,
 * changed with tick boxes on the User management screen. The keys are listed in security/Permissions.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. "ADMIN", "MANAGER", "CASHIER"

    @Column(length = 300)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    private Set<String> permissions = new HashSet<>();

    /** False until the role got its first set of permissions (then an admin's choices are never overwritten). */
    @Column(name = "permissions_initialized")
    private Boolean permissionsInitialized = false;

    /** Which version of the permission catalog this role has been offered (see Permissions.ADDED_IN). */
    @Column(name = "catalog_version")
    private Integer catalogVersion;
}
