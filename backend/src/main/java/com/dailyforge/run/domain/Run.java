package com.dailyforge.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One logged run. {@code type} is auto-assigned {@code LONG} at 10 km and locked there
 * (spec §6, §8.5) — {@link #resolveType} is the one place that decision is made, so a
 * client-supplied type below 10 km can never smuggle in {@code LONG}.
 */
@Entity
@Table(name = "run")
public class Run {

    private static final int LONG_THRESHOLD_METRES = 10_000;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private RunType type;

    @Column(name = "pace_sec_per_km", nullable = false)
    private int paceSecPerKm;

    @Column(name = "note", length = 280)
    private String note;

    @Column(name = "felt_effort")
    private Integer feltEffort;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Optimistic-concurrency guard for multi-device editing (see StaleWrite). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Run() {
        // for JPA
    }

    public static Run log(
            UUID userId, LocalDate occurredOn, int distanceMeters, int durationSeconds, RunType requestedType, String note, Integer feltEffort) {
        Run run = new Run();
        run.id = UUID.randomUUID();
        run.userId = userId;
        run.occurredOn = occurredOn;
        run.apply(distanceMeters, durationSeconds, requestedType, note, feltEffort);
        return run;
    }

    /** Re-derives {@code type} and {@code paceSecPerKm} — the fields a distance change can invalidate. */
    public void apply(int distanceMeters, int durationSeconds, RunType requestedType, String note, Integer feltEffort) {
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.type = resolveType(distanceMeters, requestedType);
        this.paceSecPerKm = (int) Math.round(durationSeconds / (distanceMeters / 1000.0));
        this.note = note;
        this.feltEffort = feltEffort;
    }

    /**
     * At or above 10 km the run is always LONG, regardless of what was asked for.
     * Below it, a caller must choose INTERVAL or TEMPO — LONG is not a valid choice
     * there (spec's "the user picks interval or tempo" is exclusive of LONG).
     */
    public static RunType resolveType(int distanceMeters, RunType requested) {
        if (distanceMeters >= LONG_THRESHOLD_METRES) {
            return RunType.LONG;
        }
        return requested;
    }

    public static boolean isLongDistance(int distanceMeters) {
        return distanceMeters >= LONG_THRESHOLD_METRES;
    }

    public void softDelete(Instant when) {
        if (this.deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public RunType getType() {
        return type;
    }

    public int getPaceSecPerKm() {
        return paceSecPerKm;
    }

    public String getNote() {
        return note;
    }

    public Integer getFeltEffort() {
        return feltEffort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public long getVersion() {
        return version;
    }

}
