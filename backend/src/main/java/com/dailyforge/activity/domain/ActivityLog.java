package com.dailyforge.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One occurrence of an activity type, on one date, worth {@code count} times its type's points. */
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "activity_type_id", nullable = false)
    private UUID activityTypeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "count", nullable = false)
    private int count;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ActivityLog() {
        // for JPA
    }

    public static ActivityLog create(UUID activityTypeId, UUID userId, LocalDate occurredOn, int count, String note) {
        ActivityLog log = new ActivityLog();
        log.id = UUID.randomUUID();
        log.activityTypeId = activityTypeId;
        log.userId = userId;
        log.occurredOn = occurredOn;
        log.count = count;
        log.note = note;
        return log;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getActivityTypeId() {
        return activityTypeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public int getCount() {
        return count;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
