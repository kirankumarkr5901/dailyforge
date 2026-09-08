package com.dailyforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token, stored only as a hash.
 *
 * The raw token is returned to the client once and never persisted, so a database dump
 * cannot be replayed as a login. Rotation means using a refresh token revokes it and
 * issues a new one. A token presented twice is usually one of this app's own contexts
 * losing a refresh race, and only sometimes a theft; replacedByHash is what lets
 * RefreshTokenService tell those apart instead of always assuming the worst.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** The token issued in its place when it was rotated — see RefreshTokenService. */
    @Column(name = "replaced_by_hash", length = 255)
    private String replacedByHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefreshToken() {
        // for JPA
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt, String deviceLabel) {
        RefreshToken token = new RefreshToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.deviceLabel = deviceLabel;
        return token;
    }

    public void revoke(Instant when) {
        if (this.revokedAt == null) {
            this.revokedAt = when;
        }
    }

    /** Revoked as part of normal rotation, recording which token took its place. */
    public void rotateTo(String replacementHash, Instant when) {
        revoke(when);
        this.replacedByHash = replacementHash;
    }

    public String getReplacedByHash() {
        return replacedByHash;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
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

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
