package com.api.inventory.controller;

import com.api.inventory.entity.AuditLog;
import com.api.inventory.entity.Role;
import com.api.inventory.entity.User;
import com.api.inventory.repository.RiderProfileRepository;
import com.api.inventory.repository.RoleRepository;
import com.api.inventory.repository.SellerProfileRepository;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.security.CurrentUser;
import com.api.inventory.security.Permissions;
import com.api.inventory.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * People and what they may do. Everything here needs users.manage.
 *
 * Safety rules (the server enforces them, whatever the screen shows):
 *  - Nobody changes, switches off or resets their OWN account here, and nobody edits their own role's permissions.
 *  - Only an ADMIN can make someone an admin, take admin away, or hand out "Manage users and roles".
 *  - There is always at least one active ADMIN.
 *  - The ADMIN role always has every permission (it cannot be edited).
 *  - SELLER and RIDER are given by approving an application in Marketplace (so their shop or bank details exist).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('users.manage')")
public class AdminController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository users;
    private final RoleRepository roles;
    private final SellerProfileRepository sellers;
    private final RiderProfileRepository riders;
    private final AccessControlService access;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private com.api.inventory.service.MarketplaceService marketplace;

    @org.springframework.beans.factory.annotation.Autowired
    void setMarketplace(com.api.inventory.service.MarketplaceService marketplace, com.api.inventory.service.CustomerService customerService) {
        this.marketplace = marketplace;
        this.customerService = customerService;
    }

    private com.api.inventory.service.CustomerService customerService;

    public AdminController(UserRepository users, RoleRepository roles, SellerProfileRepository sellers, RiderProfileRepository riders,
                           AccessControlService access, AuditService audit, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.sellers = sellers;
        this.riders = riders;
        this.access = access;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    // ================= What can be given =================

    public record PermissionView(String key, String group, String label, String description, boolean sensitive) {
    }

    @GetMapping("/permissions")
    public List<PermissionView> permissions() {
        return Permissions.CATALOG.stream()
                .map(d -> new PermissionView(d.key(), d.group(), d.label(), d.description(), d.sensitive()))
                .toList();
    }

    // ================= Roles =================

    public record RoleView(Long id, String name, String description, List<String> permissions, long userCount,
                           boolean builtIn, boolean locked) {
    }

    public record RoleRequest(String name, String description, List<String> permissions) {
    }

    @GetMapping("/roles")
    public List<RoleView> roles() {
        Map<Long, Long> counts = new HashMap<>();
        users.findAll().forEach(u -> {
            if (u.getRole() != null) counts.merge(u.getRole().getId(), 1L, Long::sum);
        });
        return roles.findAll().stream()
                .sorted(Comparator.comparingInt(AdminController::roleOrder).thenComparing(Role::getName))
                .map(r -> view(r, counts.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @PostMapping("/roles")
    @Transactional
    public RoleView createRole(@RequestBody RoleRequest request) {
        String name = roleName(request.name());
        if (roles.findByName(name).isPresent()) {
            throw new IllegalStateException("A role called " + name + " already exists.");
        }
        Set<String> wanted = checkedPermissions(request.permissions());
        requireAdminFor(wanted);

        Role role = new Role();
        role.setName(name);
        role.setDescription(text(request.description(), 300));
        role.setPermissions(new HashSet<>(wanted));
        role.setPermissionsInitialized(true);
        Role saved = roles.save(role);
        access.forgetAll();
        audit.record("ROLE_CREATED", "role " + name, "Permissions: " + String.join(", ", new TreeSet<>(wanted)));
        return view(saved, 0);
    }

    @PutMapping("/roles/{id}")
    @Transactional
    public RoleView updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        Role role = roles.findById(id).orElseThrow(() -> new IllegalStateException("Role not found."));
        String current = role.getName().toUpperCase();

        if (Permissions.ADMIN.equals(current)) {
            throw new IllegalStateException("The Admin role always has every permission and cannot be changed.");
        }
        User me = me();
        if (me.getRole() != null && me.getRole().getId().equals(role.getId())) {
            throw new AccessDeniedException("You cannot change the permissions of your own role. Ask another admin.");
        }

        Set<String> before = new TreeSet<>(role.getPermissions());
        Set<String> wanted = checkedPermissions(request.permissions());
        Set<String> added = new TreeSet<>(wanted);
        added.removeAll(before);
        requireAdminFor(added);

        if (request.name() != null && !request.name().isBlank()) {
            String newName = roleName(request.name());
            if (!newName.equals(current)) {
                if (Permissions.BUILT_IN.contains(current)) {
                    throw new IllegalStateException("Built-in roles cannot be renamed.");
                }
                if (roles.findByName(newName).isPresent()) {
                    throw new IllegalStateException("A role called " + newName + " already exists.");
                }
                role.setName(newName);
            }
        }
        role.setDescription(text(request.description(), 300));
        role.setPermissions(new HashSet<>(wanted));
        role.setPermissionsInitialized(true);
        roles.save(role);
        access.forgetAll();

        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(wanted);
        audit.record("ROLE_PERMISSIONS_CHANGED", "role " + role.getName(),
                "Added: " + (added.isEmpty() ? "-" : String.join(", ", added)) + ". Removed: " + (removed.isEmpty() ? "-" : String.join(", ", removed)));
        return view(role, users.findAll().stream().filter(u -> u.getRole() != null && u.getRole().getId().equals(id)).count());
    }

    @DeleteMapping("/roles/{id}")
    @Transactional
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        Role role = roles.findById(id).orElseThrow(() -> new IllegalStateException("Role not found."));
        if (Permissions.BUILT_IN.contains(role.getName().toUpperCase())) {
            throw new IllegalStateException("Built-in roles cannot be deleted.");
        }
        long inUse = users.findAll().stream().filter(u -> u.getRole() != null && u.getRole().getId().equals(id)).count();
        if (inUse > 0) {
            throw new IllegalStateException(inUse + " people have this role. Give them another role first.");
        }
        requireAdminFor(role.getPermissions());
        roles.delete(role);
        access.forgetAll();
        audit.record("ROLE_DELETED", "role " + role.getName(), null);
        return ResponseEntity.noContent().build();
    }

    // ================= People =================

    public record UserView(Long id, String name, String email, String phone, Long roleId, String roleName,
                           boolean active, Instant lastLoginAt, Instant createdAt, String partner, String partnerStatus) {
    }

    public record NewUserRequest(String name, String email, String phone, String password, Long roleId) {
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .map(this::view)
                .toList();
    }

    /** An admin adds a person directly (staff, or a customer at the counter). */
    @PostMapping("/users")
    @Transactional
    public UserView createUser(@RequestBody NewUserRequest request) {
        String name = text(request.name(), 120);
        String email = request.email() == null ? "" : request.email().trim();
        String phone = request.phone() == null ? "" : request.phone().trim();
        String password = request.password() == null ? "" : request.password();
        if (name == null) throw new IllegalStateException("Enter the full name.");
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw new IllegalStateException("Enter a valid email address.");
        if (!phone.matches("^[0-9]{8}$")) throw new IllegalStateException("Enter an 8-digit phone number.");
        if (password.length() < 6) throw new IllegalStateException("The password needs at least 6 characters.");
        if (users.existsByEmail(email)) throw new IllegalStateException("This email is already registered.");
        if (users.existsByPhone(phone)) throw new IllegalStateException("This phone number is already registered.");

        Role role = request.roleId() == null ? roles.findByName(Permissions.USER).orElseThrow()
                : roles.findById(request.roleId()).orElseThrow(() -> new IllegalStateException("Role not found."));
        if (Permissions.SELLER.equalsIgnoreCase(role.getName()) || Permissions.RIDER.equalsIgnoreCase(role.getName())) {
            throw new IllegalStateException("Add the person as a Customer first, then choose Seller or Delivery driver to fill in their details.");
        }
        requireAssignable(role);

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setActive(true);
        User saved = users.save(user);
        if (Permissions.USER.equalsIgnoreCase(role.getName())) {
            customerService.linkUser(saved);
        }
        audit.record("USER_CREATED", who(saved), "Role " + role.getName());
        return view(saved);
    }

    /** Kept at the same address as before, so older screens still work. */
    @PutMapping("/users/{userId}/role")
    @Transactional
    public UserView updateUserRole(@PathVariable Long userId, @RequestParam Long roleId) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found."));
        requireNotMe(user, "change your own role");
        Role newRole = roles.findById(roleId).orElseThrow(() -> new IllegalStateException("Role not found."));
        Role oldRole = user.getRole();
        if (oldRole != null && oldRole.getId().equals(newRole.getId())) {
            return view(user);
        }
        requireAssignable(newRole);
        leaveRole(user, oldRole);
        String target = newRole.getName().toUpperCase();
        if (Permissions.SELLER.equals(target) || Permissions.RIDER.equals(target)) {
            // they applied before: approve that application (its details and documents are already there)
            marketplace.approveExisting(user, target, CurrentUser.email());
        }
        user.setRole(newRole);
        users.save(user);
        audit.record("USER_ROLE_CHANGED", who(user), (oldRole == null ? "-" : oldRole.getName()) + " -> " + newRole.getName());
        return view(user);
    }

    /** Makes someone a seller directly (no application yet): the admin fills in the shop and bank details. */
    @PostMapping("/users/{userId}/make-seller")
    @Transactional
    public UserView makeSeller(@PathVariable Long userId, @RequestBody com.api.inventory.dto.MarketplaceDTOs.SellerApplication form) {
        return makePartner(userId, Permissions.SELLER, user -> marketplace.adminCreateSeller(user, form, CurrentUser.email()));
    }

    /** Makes someone a delivery driver directly: the admin fills in the vehicle, licence and bank details. */
    @PostMapping("/users/{userId}/make-driver")
    @Transactional
    public UserView makeDriver(@PathVariable Long userId, @RequestBody com.api.inventory.dto.MarketplaceDTOs.RiderApplication form) {
        return makePartner(userId, Permissions.RIDER, user -> marketplace.adminCreateRider(user, form, CurrentUser.email()));
    }

    private UserView makePartner(Long userId, String roleName, java.util.function.Consumer<User> createProfile) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found."));
        requireNotMe(user, "change your own role");
        Role newRole = roles.findByName(roleName).orElseThrow(() -> new IllegalStateException("Role " + roleName + " is missing."));
        Role oldRole = user.getRole();
        leaveRole(user, oldRole);
        createProfile.accept(user);
        user.setRole(newRole);
        users.save(user);
        audit.record("USER_ROLE_CHANGED", who(user), (oldRole == null ? "-" : oldRole.getName()) + " -> " + roleName + " (details added by admin)");
        return view(user);
    }

    /** Checks before someone leaves their current role, and pauses a seller/driver account they leave. */
    private void leaveRole(User user, Role oldRole) {
        if (oldRole == null) {
            return;
        }
        requireAdminFor(access.permissionsOf(oldRole.getName()).contains("users.manage") || Permissions.ADMIN.equalsIgnoreCase(oldRole.getName())
                ? Set.of("users.manage") : Set.of());
        if (Permissions.ADMIN.equalsIgnoreCase(oldRole.getName())) {
            requireAnotherActiveAdmin(user);
        }
        // leaving the seller or driver role pauses their marketplace account, so nothing is half-active
        pausePartner(user, oldRole.getName());
    }

    public record ActiveRequest(Boolean active) {
    }

    /** Switch an account off (they are signed out at once) or on again. Nothing is deleted. */
    @PutMapping("/users/{userId}/active")
    @Transactional
    public UserView setActive(@PathVariable Long userId, @RequestBody ActiveRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found."));
        requireNotMe(user, "switch off your own account");
        boolean active = request == null || !Boolean.FALSE.equals(request.active());
        if (user.getRole() != null && (Permissions.ADMIN.equalsIgnoreCase(user.getRole().getName())
                || access.permissionsOf(user.getRole().getName()).contains("users.manage"))) {
            requireAdminFor(Set.of("users.manage"));
        }
        if (!active && user.getRole() != null && Permissions.ADMIN.equalsIgnoreCase(user.getRole().getName())) {
            requireAnotherActiveAdmin(user);
        }
        user.setActive(active);
        users.save(user);
        audit.record(active ? "USER_REACTIVATED" : "USER_DEACTIVATED", who(user), null);
        return view(user);
    }

    public record TemporaryPassword(String password) {
    }

    /** For "I forgot my password": a new temporary password, shown ONCE to the admin to pass on. */
    @PostMapping("/users/{userId}/reset-password")
    @Transactional
    public TemporaryPassword resetPassword(@PathVariable Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found."));
        requireNotMe(user, "reset your own password here (use My profile)");
        if (user.getRole() != null && (Permissions.ADMIN.equalsIgnoreCase(user.getRole().getName())
                || access.permissionsOf(user.getRole().getName()).contains("users.manage"))) {
            requireAdminFor(Set.of("users.manage"));
        }
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        user.setPassword(passwordEncoder.encode(password.toString()));
        users.save(user);
        audit.record("USER_PASSWORD_RESET", who(user), null);
        return new TemporaryPassword(password.toString());
    }

    // ================= Activity =================

    public record AuditView(Long id, Instant at, String actor, String action, String target, String details) {
    }

    @GetMapping("/audit")
    public List<AuditView> auditLog(@RequestParam(defaultValue = "200") int limit) {
        return audit.latest(limit).stream()
                .map((AuditLog a) -> new AuditView(a.getId(), a.getAt(), a.getActor(), a.getAction(), a.getTarget(), a.getDetails()))
                .toList();
    }

    // ================= Rules =================

    private User me() {
        return users.findByEmail(CurrentUser.email()).orElseThrow(() -> new AccessDeniedException("Please sign in."));
    }

    private boolean iAmAdmin() {
        User me = me();
        return me.getRole() != null && Permissions.ADMIN.equalsIgnoreCase(me.getRole().getName());
    }

    private void requireNotMe(User user, String what) {
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(CurrentUser.email())) {
            throw new AccessDeniedException("You cannot " + what + ".");
        }
    }

    /** Handing out admin-level powers needs an admin. */
    private void requireAdminFor(Collection<String> permissions) {
        boolean powerful = permissions.contains("users.manage") || permissions.contains("marketplace.manage");
        if (powerful && !iAmAdmin()) {
            throw new AccessDeniedException("Only an administrator can give or take away user management or marketplace powers.");
        }
    }

    private void requireAssignable(Role role) {
        String name = role.getName().toUpperCase();
        if (Permissions.ADMIN.equals(name)) {
            requireAdminFor(Set.of("users.manage"));
        } else {
            requireAdminFor(role.getPermissions());
        }
    }

    private void requireAnotherActiveAdmin(User leaving) {
        long others = users.findAll().stream()
                .filter(u -> !u.getId().equals(leaving.getId()) && u.isActive() && u.getRole() != null
                        && Permissions.ADMIN.equalsIgnoreCase(u.getRole().getName()))
                .count();
        if (others == 0) {
            throw new IllegalStateException("This is the last active administrator. Make someone else an admin first.");
        }
    }

    private void pausePartner(User user, String oldRole) {
        if (Permissions.SELLER.equalsIgnoreCase(oldRole)) {
            sellers.findByUserEmail(user.getEmail()).ifPresent(s -> {
                s.setStatus("SUSPENDED");
                s.setStatusNote("Role changed by an administrator.");
                sellers.save(s);
            });
        } else if (Permissions.RIDER.equalsIgnoreCase(oldRole)) {
            riders.findByUserEmail(user.getEmail()).ifPresent(r -> {
                r.setStatus("SUSPENDED");
                r.setStatusNote("Role changed by an administrator.");
                riders.save(r);
            });
        }
    }

    private Set<String> checkedPermissions(List<String> keys) {
        Set<String> wanted = new LinkedHashSet<>();
        if (keys != null) {
            for (String k : keys) {
                if (!Permissions.ALL_KEYS.contains(k)) {
                    throw new IllegalStateException("Unknown permission: " + k);
                }
                wanted.add(k);
            }
        }
        return wanted;
    }

    private static String roleName(String raw) {
        String name = raw == null ? "" : raw.trim().toUpperCase().replaceAll("[^A-Z0-9_ ]", "").replace(' ', '_');
        if (name.length() < 3 || name.length() > 30) {
            throw new IllegalStateException("A role name needs 3 to 30 letters or numbers.");
        }
        return name;
    }

    private static String text(String value, int max) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? null : v.substring(0, Math.min(max, v.length()));
    }

    private static int roleOrder(Role r) {
        List<String> order = List.of("ADMIN", "MANAGER", "CONTROLLER", "SELLER", "RIDER", "USER");
        int i = order.indexOf(r.getName().toUpperCase());
        return i < 0 ? 3 : i; // custom roles sit with the staff roles
    }

    private RoleView view(Role r, long count) {
        String name = r.getName().toUpperCase();
        List<String> perms = new ArrayList<>(Permissions.ADMIN.equals(name) ? Permissions.ADMIN_KEYS : r.getPermissions());
        Collections.sort(perms);
        return new RoleView(r.getId(), r.getName(), r.getDescription(), perms, count,
                Permissions.BUILT_IN.contains(name), Permissions.ADMIN.equals(name));
    }

    private UserView view(User u) {
        String partner = null;
        String partnerStatus = null;
        var seller = sellers.findByUserEmail(u.getEmail());
        if (seller.isPresent()) {
            partner = "SELLER";
            partnerStatus = seller.get().getStatus();
        } else {
            var rider = riders.findByUserEmail(u.getEmail());
            if (rider.isPresent()) {
                partner = "RIDER";
                partnerStatus = rider.get().getStatus();
            }
        }
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                u.getRole() == null ? null : u.getRole().getId(), u.getRole() == null ? null : u.getRole().getName(),
                u.isActive(), u.getLastLoginAt(), u.getCreatedAt(), partner, partnerStatus);
    }

    private static String who(User u) {
        return "user " + u.getId() + " (" + u.getEmail() + ")";
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> refused(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
