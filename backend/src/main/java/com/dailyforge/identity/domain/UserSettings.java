package com.dailyforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Per-user settings.
 *
 * {@code timeZone} is the one that matters most: every streak, heatmap cell and daily
 * bonus in the app resolves through it (spec §4.2). It is captured from the browser at
 * signup and can be corrected in Settings.
 *
 * {@code weekStart} is fixed to MONDAY by the plan; it exists as a column so the choice
 * is visible and changeable later without a migration.
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";

    @Column(name = "unit_system", nullable = false, length = 10)
    private String unitSystem = "METRIC";

    @Column(name = "theme", nullable = false, length = 10)
    private String theme = "SYSTEM";

    @Column(name = "week_start", nullable = false, length = 10)
    private String weekStart = "MONDAY";

    /** Asked once, at the first habit's creation (spec §8.2). Zero until then. */
    @Column(name = "commitment_bonus", nullable = false)
    private int commitmentBonus = 0;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic-concurrency guard for multi-device editing (see StaleWrite). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserSettings() {
        // for JPA
    }

    public static UserSettings forUser(UUID userId, String timeZone) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.timeZone = timeZone == null || timeZone.isBlank() ? "UTC" : timeZone;
        return settings;
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

    public UUID getUserId() {
        return userId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getUnitSystem() {
        return unitSystem;
    }

    public void setUnitSystem(String unitSystem) {
        this.unitSystem = unitSystem;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getWeekStart() {
        return weekStart;
    }

    public int getCommitmentBonus() {
        return commitmentBonus;
    }

    public void setCommitmentBonus(int commitmentBonus) {
        this.commitmentBonus = commitmentBonus;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public LocalTime getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(LocalTime reminderTime) {
        this.reminderTime = reminderTime;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public void setOnboardingCompletedAt(Instant onboardingCompletedAt) {
        this.onboardingCompletedAt = onboardingCompletedAt;
    }
    public long getVersion() {
        return version;
    }

}
