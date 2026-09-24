package com.dailyforge.activity.domain;

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
 * A user-defined one-off activity (spec §6 "activity") — something worth a point value
 * every time it happens, positive ("meditated") or negative ("skipped a meal"), outside
 * the structured habit/workout/run trackers. {@code points} is always stored as a
 * positive magnitude; the sign is applied at award time from {@code polarity}, the same
 * way a reward's cost is a positive number that becomes a negative award.
 */
@Entity
@Table(name = "activity_type")
public class ActivityType {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "polarity", nullable = false, length = 10)
    private ActivityPolarity polarity;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivityType() {
        // for JPA
    }

    public static ActivityType create(UUID userId, String name, ActivityPolarity polarity, int points, String icon, int sortOrder) {
        ActivityType type = new ActivityType();
        type.id = UUID.randomUUID();
        type.userId = userId;
        type.name = name;
        type.polarity = polarity;
        type.points = points;
        type.icon = icon;
        type.sortOrder = sortOrder;
        return type;
    }

    public void archive(Instant when) {
        if (this.archivedAt == null) {
            this.archivedAt = when;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** The award amount for one occurrence, sign already applied. */
    public int signedPoints() {
        return polarity == ActivityPolarity.POSITIVE ? points : -points;
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

    public String getName() {
        return name;
    }

    public ActivityPolarity getPolarity() {
        return polarity;
    }

    public int getPoints() {
        return points;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
