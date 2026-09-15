package com.dailyforge.habit.domain;

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
 * One day's raw tick for one habit. Unlike {@code points_entry} this is a plain mutable
 * row — reconciliation is what keeps the immutable ledger correct as this changes, not
 * this row's own immutability. Ticking, unticking and re-ticking the same day is a
 * genuine update, never a fresh row.
 */
@Entity
@Table(name = "habit_log")
public class HabitLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 10)
    private HabitLogState state;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    /** Set once the day's date has arrived and reconciliation has awarded its points. */
    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HabitLog() {
        // for JPA
    }

    public static HabitLog create(UUID habitId, LocalDate occurredOn, HabitLogState state) {
        HabitLog log = new HabitLog();
        log.id = UUID.randomUUID();
        log.habitId = habitId;
        log.occurredOn = occurredOn;
        log.state = state;
        log.loggedAt = Instant.now();
        return log;
    }

    public void setState(HabitLogState state) {
        this.state = state;
        this.loggedAt = Instant.now();
    }

    public void markSettled(Instant when) {
        this.settledAt = when;
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

    public UUID getHabitId() {
        return habitId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public HabitLogState getState() {
        return state;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
