package com.dailyforge.workout.domain;

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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One exercise, shared across every plan that uses it (spec §6: "Exercises are shared
 * across plans and reusable across future plans"). {@code ownerUserId} is null for the
 * system catalog and set for a user's own custom exercise, visible only to them.
 */
@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "search_name", nullable = false, length = 120)
    private String searchName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private ExerciseKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment", nullable = false, length = 12)
    private Equipment equipment;

    /** Comma-joined; short, display-only, never queried by individual group. */
    @Column(name = "muscle_groups", nullable = false, length = 200)
    private String muscleGroups = "";

    @Column(name = "is_elite", nullable = false)
    private boolean elite;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic-concurrency guard for multi-device editing (see StaleWrite). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Exercise() {
        // for JPA
    }

    public static Exercise create(
            UUID ownerUserId,
            String name,
            ExerciseKind kind,
            Equipment equipment,
            List<String> muscleGroups,
            boolean elite) {
        Exercise exercise = new Exercise();
        exercise.id = UUID.randomUUID();
        exercise.ownerUserId = ownerUserId;
        exercise.name = name;
        exercise.searchName = normalise(name);
        exercise.kind = kind;
        exercise.equipment = equipment;
        exercise.muscleGroups = String.join(",", muscleGroups);
        exercise.elite = elite;
        return exercise;
    }

    public static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public void update(String name, ExerciseKind kind, Equipment equipment, List<String> muscleGroups, boolean elite) {
        this.name = name;
        this.searchName = normalise(name);
        this.kind = kind;
        this.equipment = equipment;
        this.muscleGroups = String.join(",", muscleGroups);
        this.elite = elite;
    }

    public void archive(Instant when) {
        if (this.archivedAt == null) {
            this.archivedAt = when;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId != null && ownerUserId.equals(userId);
    }

    public boolean isVisibleTo(UUID userId) {
        return ownerUserId == null || ownerUserId.equals(userId);
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

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getSearchName() {
        return searchName;
    }

    public ExerciseKind getKind() {
        return kind;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public List<String> getMuscleGroups() {
        return muscleGroups.isBlank() ? List.of() : List.of(muscleGroups.split(","));
    }

    public boolean isElite() {
        return elite;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
    public long getVersion() {
        return version;
    }

}
