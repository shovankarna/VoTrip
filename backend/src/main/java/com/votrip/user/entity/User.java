package com.votrip.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A VoTrip account, per docs/02-SRS-ERD.md section 5.
 *
 * <p>Rows are never hard-deleted - {@link #isActive} is flipped instead - because nearly every
 * other table holds a {@code user_id} FK that financial and historical records depend on.
 *
 * <p>Identity fields are populated from a verified Firebase token, never from a request body.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true, updatable = false, length = 128)
    private String firebaseUid;

    @Column(name = "email", unique = true, length = 320)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "is_super_admin", nullable = false)
    private boolean superAdmin = false;

    /**
     * Defaults to true here as well as in the migration, so a row inserted through JPA lands
     * active without relying on the database DEFAULT (JPA sends the column explicitly).
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    private User(UUID id, String firebaseUid, String email, String displayName, String phone,
            String avatarUrl, Instant createdAt) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
    }

    /**
     * Builds a brand-new account from verified token claims. {@code superAdmin} stays false and
     * {@code active} stays true - neither is settable from outside, and no endpoint changes them
     * yet (issue #3, Out of scope).
     */
    public static User provisionFromToken(
            String firebaseUid, String email, String displayName, String avatarUrl) {
        return new User(
                UUID.randomUUID(), firebaseUid, email, displayName, null, avatarUrl, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
