package com.dailyforge.points.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line in the ledger. Immutable and append-only (spec §5.1, and PRODUCT.md's
 * invariant 2): nothing in this codebase may update {@code amount} or delete a
 * row. An undo is a second row whose {@code reversesId} points at the first.
 *
 * There is deliberately no setter for {@code amount}, {@code category}, {@code ruleCode}
 * or {@code sourceId} — the only mutation this entity permits after construction is
 * {@link #markReversed()}, which flips one boolean once. Anything else is a new entry.
 */
@Entity
@Table(name = "points_entry")
public class PointsEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PointsCategory category;

    @Column(name = "rule_code", nullable = false, length = 60)
    private String ruleCode;

    /** Signed. Penalties and reversals are negative. */
    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "reverses_id")
    private UUID reversesId;

    @Column(name = "reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PointsEntry() {
        // for JPA
    }

    /** The one constructor. Every field but {@code reversed} is fixed for the row's life. */
    public static PointsEntry create(
            UUID userId,
            LocalDate occurredOn,
            PointsCategory category,
            String ruleCode,
            int amount,
            String sourceType,
            UUID sourceId,
            UUID reversesId,
            String description,
            String idempotencyKey) {
        PointsEntry entry = new PointsEntry();
        entry.id = UUID.randomUUID();
        entry.userId = userId;
        entry.occurredOn = occurredOn;
        entry.category = category;
        entry.ruleCode = ruleCode;
        entry.amount = amount;
        entry.sourceType = sourceType;
        entry.sourceId = sourceId;
        entry.reversesId = reversesId;
        entry.description = description;
        entry.idempotencyKey = idempotencyKey;
        return entry;
    }

    /** Flips once, when a compensating entry has been written for this one. */
    public void markReversed() {
        this.reversed = true;
    }

    public boolean isReversal() {
        return reversesId != null;
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

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public PointsCategory getCategory() {
        return category;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public int getAmount() {
        return amount;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getReversesId() {
        return reversesId;
    }

    public boolean isReversed() {
        return reversed;
    }

    public String getDescription() {
        return description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
