package com.api.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = "phone") // Optional: enforce unique phone
})
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Column(unique = true)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^[0-9]{8,15}$",
        message = "Phone number must be between 8 and 15 digits (no spaces or symbols)"
    )
    @Column(unique = true, nullable = false)
    private String phone;

    @NotBlank(message = "Password is required")
    private String password;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role; // Default role

    /** Switched off by an admin: cannot sign in, and is signed out at once. Empty (older rows) = active. */
    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "last_login_at")
    private java.time.Instant lastLoginAt;

    @Column(name = "created_at")
    private java.time.Instant createdAt;

    /** When the password last changed. Sessions started before it are signed out (see SessionAuthenticationFilter). */
    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /** A number stored in the session at sign-in: a later password change makes older sessions invalid. */
    public long passwordStamp() {
        return passwordChangedAt == null ? 0L : passwordChangedAt.toEpochMilli();
    }

    public boolean isActive() {
        return !Boolean.FALSE.equals(active);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}
    
    
}