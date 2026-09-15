package com.dailyforge.habit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A cache of what the reconciler last computed from raw logs — read for fast display
 * (the habit board's streak numbers), never as the source of truth for whether a bonus
 * is owed. That answer always comes from recomputing against {@code habit_log}.
 */
@Entity
@Table(name = "habit_streak")
public class HabitStreak {

    @Id
    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak;

    @Column(name = "last_awarded_multiple_of7", nullable = false)
    private int lastAwardedMultipleOf7;

    @Column(name = "last_completed_date")
    private LocalDate lastCompletedDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HabitStreak() {
        // for JPA
    }

    public static HabitStreak empty(UUID habitId) {
        HabitStreak streak = new HabitStreak();
        streak.habitId = habitId;
        streak.updatedAt = Instant.now();
        return streak;
    }

    public void rebuild(int currentStreak, int bestStreak, int lastAwardedMultipleOf7, LocalDate lastCompletedDate) {
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
        this.lastAwardedMultipleOf7 = lastAwardedMultipleOf7;
        this.lastCompletedDate = lastCompletedDate;
        this.updatedAt = Instant.now();
    }

    public UUID getHabitId() {
        return habitId;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public int getLastAwardedMultipleOf7() {
        return lastAwardedMultipleOf7;
    }

    public LocalDate getLastCompletedDate() {
        return lastCompletedDate;
    }
}
