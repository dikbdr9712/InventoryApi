package com.api.inventory.security;

import com.api.inventory.entity.Role;
import com.api.inventory.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each role may do, read from the database and kept in memory (it is checked on every request).
 * The memory copy is thrown away whenever an admin changes a role, so a change works on the very next click.
 *
 * On start-up it creates the built-in roles that are missing, and gives every role that was never set up its
 * default permissions. Choices an admin made are never overwritten.
 */
@Service
@Order(0)
public class AccessControlService implements ApplicationRunner {

    private final RoleRepository roles;
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

    public AccessControlService(RoleRepository roles) {
        this.roles = roles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : Permissions.BUILT_IN) {
            if (roles.findByName(name).isEmpty()) {
                Role role = new Role();
                role.setName(name);
                roles.save(role);
            }
        }
        for (Role role : roles.findAll()) {
            boolean changed = false;
            if (!Boolean.TRUE.equals(role.getPermissionsInitialized())) {
                role.setPermissions(new HashSet<>(Permissions.defaultsFor(role.getName())));
                role.setPermissionsInitialized(true);
                role.setCatalogVersion(Permissions.CATALOG_VERSION); // the defaults already include everything
                changed = true;
            }
            // permissions added to the catalog since this role was set up: give the role's defaults among them, once
            int seen = role.getCatalogVersion() == null ? 1 : role.getCatalogVersion();
            if (seen < Permissions.CATALOG_VERSION) {
                Set<String> defaults = Permissions.defaultsFor(role.getName());
                for (int v = seen + 1; v <= Permissions.CATALOG_VERSION; v++) {
                    for (String p : Permissions.ADDED_IN.getOrDefault(v, Set.of())) {
                        if (defaults.contains(p)) {
                            role.getPermissions().add(p);
                        }
                    }
                }
                role.setCatalogVersion(Permissions.CATALOG_VERSION);
                changed = true;
            }
            // One-time upgrade: "seller.portal" / "rider.portal" used to mean everything for a seller / driver.
            // A role that has the portal but none of the detailed rights gets them, so nobody loses access.
            changed |= upgrade(role, "seller.portal", Permissions.SELLER_ALL);
            changed |= upgrade(role, "rider.portal", Permissions.RIDER_ALL);
            if (role.getDescription() == null && Permissions.DESCRIPTIONS.containsKey(role.getName().toUpperCase())) {
                role.setDescription(Permissions.DESCRIPTIONS.get(role.getName().toUpperCase()));
                changed = true;
            }
            if (changed) {
                roles.save(role);
            }
        }
        forgetAll();
    }

    private static boolean upgrade(Role role, String portal, Set<String> all) {
        Set<String> perms = role.getPermissions();
        if (!perms.contains(portal)) {
            return false;
        }
        boolean hasDetail = all.stream().anyMatch(p -> !p.equals(portal) && perms.contains(p));
        if (hasDetail) {
            return false;
        }
        perms.addAll(all);
        return true;
    }

    /** The permissions of a role. ADMIN always has all of them. Unknown roles have none. */
    public Set<String> permissionsOf(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Set.of();
        }
        String key = roleName.trim().toUpperCase();
        if (Permissions.ADMIN.equals(key)) {
            return Permissions.ADMIN_KEYS;
        }
        return cache.computeIfAbsent(key, k -> roles.findByName(k)
                .map(r -> {
                    Set<String> valid = new LinkedHashSet<>(r.getPermissions());
                    valid.retainAll(Permissions.ALL_KEYS); // ignore anything no longer in the catalog
                    return Set.copyOf(valid);
                })
                .orElse(Set.of()));
    }

    public boolean roleHas(String roleName, String permission) {
        return permissionsOf(roleName).contains(permission);
    }

    /** Call after any change to roles or their permissions. */
    public void forgetAll() {
        cache.clear();
    }
}
