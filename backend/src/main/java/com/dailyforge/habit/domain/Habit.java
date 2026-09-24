package com.dailyforge.habit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A habit. {@code baseBonus} and {@code bonusMultiplier} are captured here at creation
 * time (spec §5.4 defaults 20 and 1.5) rather than only living in
 * {@code points_rule_config}, because the plan lets each habit set its own — the config
 * row only supplies the default a new habit starts from.
 */
@Entity
@Table(name = "habit")
public class Habit {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon;

    @Column(name = "points", nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private HabitType type;

    @Column(name = "penalty_points", nullable = false)
    private int penaltyPoints;

    @Column(name = "base_bonus", nullable = false)
    private int baseBonus;

    @Column(name = "bonus_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal bonusMultiplier;

    @Column(name = "schedule_days", nullable = false)
    private int scheduleDays = ScheduleDays.EVERY_DAY;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

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

    protected Habit() {
        // for JPA
    }

    public static Habit create(
            UUID userId,
            String name,
            String icon,
            int points,
            HabitType type,
            int penaltyPoints,
            int baseBonus,
            BigDecimal bonusMultiplier,
            int scheduleDays,
            LocalDate activeFrom,
            int sortOrder) {
        ScheduleDays.validate(scheduleDays);

        Habit habit = new Habit();
        habit.id = UUID.randomUUID();
        habit.userId = userId;
        habit.name = name;
        habit.icon = icon;
        habit.points = points;
        habit.type = type;
        habit.penaltyPoints = type == HabitType.STRICT ? penaltyPoints : 0;
        habit.baseBonus = baseBonus;
        habit.bonusMultiplier = bonusMultiplier;
        habit.scheduleDays = scheduleDays;
        habit.activeFrom = activeFrom;
        habit.sortOrder = sortOrder;
        return habit;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void updateIcon(String icon) {
        this.icon = icon;
    }

    public void updatePoints(int points) {
        this.points = points;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updateSchedule(int scheduleDays) {
        ScheduleDays.validate(scheduleDays);
        this.scheduleDays = scheduleDays;
    }

    /** Strict-only fields collapse to their neutral value the moment a habit becomes normal. */
    public void updateType(HabitType type, int penaltyPoints) {
        this.type = type;
        this.penaltyPoints = type == HabitType.STRICT ? penaltyPoints : 0;
    }

    public void archive(Instant when) {
        if (this.archivedAt == null) {
            this.archivedAt = when;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isScheduledOn(LocalDate date) {
        return !date.isBefore(activeFrom) && ScheduleDays.isScheduled(scheduleDays, date);
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

    public String getIcon() {
        return icon;
    }

    public int getPoints() {
        return points;
    }

    public HabitType getType() {
        return type;
    }

    public int getPenaltyPoints() {
        return penaltyPoints;
    }

    public int getBaseBonus() {
        return baseBonus;
    }

    public BigDecimal getBonusMultiplier() {
        return bonusMultiplier;
    }

    public int getScheduleDays() {
        return scheduleDays;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDate getActiveFrom() {
        return activeFrom;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
    public long getVersion() {
        return version;
    }

}
