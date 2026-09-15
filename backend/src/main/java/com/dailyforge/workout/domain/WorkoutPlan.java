package com.dailyforge.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A workout plan: a number of days, each with its own editable label (spec §8.2: "Days
 * are then listed as cards... editable label"). Day 0 is never a real day — it is the
 * "Extras" bucket for exercises not yet assigned to one (spec §8.2).
 */
@Entity
@Table(name = "workout_plan")
public class WorkoutPlan {

    private static final String LABEL_SEPARATOR = "|";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "day_count", nullable = false)
    private int dayCount;

    @Column(name = "day_labels", nullable = false, length = 400)
    private String dayLabels = "";

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkoutPlan() {
        // for JPA
    }

    public static WorkoutPlan create(UUID userId, String name, int dayCount, boolean active) {
        if (dayCount < 1 || dayCount > 14) {
            throw new IllegalArgumentException("dayCount must be between 1 and 14");
        }
        WorkoutPlan plan = new WorkoutPlan();
        plan.id = UUID.randomUUID();
        plan.userId = userId;
        plan.name = name;
        plan.dayCount = dayCount;
        plan.active = active;
        return plan;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void relabelDay(int dayIndex, String label) {
        List<String> labels = new ArrayList<>(labelList());
        while (labels.size() < dayIndex) {
            labels.add(null);
        }
        labels.set(dayIndex - 1, label);
        this.dayLabels =
                labels.stream().map(l -> l == null ? "" : l.replace(LABEL_SEPARATOR, "/")).reduce(
                        (a, b) -> a + LABEL_SEPARATOR + b).orElse("");
    }

    public String labelFor(int dayIndex) {
        List<String> labels = labelList();
        if (dayIndex >= 1 && dayIndex <= labels.size()) {
            String label = labels.get(dayIndex - 1);
            if (label != null && !label.isBlank()) {
                return label;
            }
        }
        return "Day " + dayIndex;
    }

    private List<String> labelList() {
        if (dayLabels.isBlank()) {
            return List.of();
        }
        String[] parts = dayLabels.split("\\" + LABEL_SEPARATOR, -1);
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            labels.add(part.isBlank() ? null : part);
        }
        return labels;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void archive(Instant when) {
        if (this.archivedAt == null) {
            this.archivedAt = when;
            this.active = false;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
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

    public int getDayCount() {
        return dayCount;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
