package com.cypher.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A CYPHER user.
 *
 * <p>Maps the {@code users} table defined in
 * {@code db/migration/V1__initial_schema.sql}. The schema stores the name in two
 * columns ({@code first_name}, {@code last_name}) and links each user to a row
 * in {@code roles}. This replaced an earlier single {@code full_name} column
 * that had no role association at all.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * A BCrypt hash. The getter is {@code @JsonIgnore}d so it can never leak
     * through a REST response.
     */
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Owned by the database (DEFAULT CURRENT_TIMESTAMP + update triggers).
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public User() {
    }

    public User(String firstName, String lastName, String email, String passwordHash, Role role) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // ------------------------------------------------------------- getters ---

    public Long getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /** Convenience for callers that want a single display name. Not persisted. */
    @Transient
    public String getFullName() {
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null || lastName.isBlank()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public String getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }

    @Transient
    public String getRoleName() {
        return role == null ? null : role.getRoleName();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ------------------------------------------------------------- setters ---

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Splits a single display name on the first space so callers written against
     * the old single-column model keep working.
     */
    public void setFullName(String fullName) {
        if (fullName == null) {
            this.firstName = null;
            this.lastName = null;
            return;
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            this.firstName = trimmed;
            this.lastName = "";
        } else {
            this.firstName = trimmed.substring(0, space);
            this.lastName = trimmed.substring(space + 1).trim();
        }
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
