package com.dailyforge.insight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One quote (spec §8.1, §6). {@code verifiedAt} is null until a human has actually
 * checked the attribution against a primary source — it is never set just because a
 * row exists in the seed data.
 */
@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "author", nullable = false, length = 120)
    private String author;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Quote() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getAuthor() {
        return author;
    }

    public String getSource() {
        return source;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isActive() {
        return active;
    }
}
