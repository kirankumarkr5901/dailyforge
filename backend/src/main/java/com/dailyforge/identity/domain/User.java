package com.dailyforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person.
 *
 * A user signs in with a password, with Google, or with both — the database enforces
 * that at least one exists. Linking is by verified email: signing in with Google using
 * an address that already has a password account attaches to that account rather than
 * creating a second one, because two accounts for one person is the bug that quietly
 * splits someone's history in half.
 *
 * Email is stored lower-cased. The schema has a plain unique constraint rather than
 * citext, because the same migration has to run on H2 (spec §3.2), so normalisation is
 * the application's job — see {@link #normaliseEmail}.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    /** Null for a Google-only account. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Google's stable subject id. Null until the account is linked to Google. */
    @Column(name = "google_sub", length = 255)
    private String googleSub;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // for JPA
    }

    private User(UUID id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public static User withPassword(String email, String displayName, String passwordHash) {
        User user = new User(UUID.randomUUID(), normaliseEmail(email), displayName);
        user.passwordHash = passwordHash;
        return user;
    }

    public static User withGoogle(String email, String displayName, String googleSub) {
        User user = new User(UUID.randomUUID(), normaliseEmail(email), displayName);
        user.googleSub = googleSub;
        return user;
    }

    /**
     * Case and surrounding whitespace are not part of an email address as far as this
     * app is concerned. Normalising on the way in is what makes the plain unique
     * constraint behave like citext.
     */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Attaches a Google identity to an existing account. */
    public void linkGoogle(String googleSub) {
        this.googleSub = googleSub;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
