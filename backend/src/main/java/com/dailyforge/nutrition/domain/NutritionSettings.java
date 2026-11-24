package com.dailyforge.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The protein target, plus whether the starter food list was already handed out.
 * Created on first use. {@code foodsSeeded} is what stops the starter list coming back
 * after the user deletes every food on purpose.
 */
@Entity
@Table(name = "nutrition_settings")
public class NutritionSettings {

    public static final int DEFAULT_TARGET_G = 140;
    public static final int DEFAULT_CALORIE_BUDGET = 2250;
    public static final int DEFAULT_FAT_TARGET_G = 70;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "protein_target_g", nullable = false)
    private int proteinTargetG = DEFAULT_TARGET_G;

    /** Calories to stay under each day. */
    @Column(name = "calorie_budget", nullable = false)
    private int calorieBudget = DEFAULT_CALORIE_BUDGET;

    @Column(name = "fat_target_g", nullable = false)
    private int fatTargetG = DEFAULT_FAT_TARGET_G;

    @Column(name = "foods_seeded", nullable = false)
    private boolean foodsSeeded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NutritionSettings() {
        // for JPA
    }

    public static NutritionSettings defaultsFor(UUID userId) {
        NutritionSettings s = new NutritionSettings();
        s.userId = userId;
        return s;
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

    public int getProteinTargetG() {
        return proteinTargetG;
    }

    public void setProteinTargetG(int proteinTargetG) {
        this.proteinTargetG = proteinTargetG;
    }

    public int getCalorieBudget() {
        return calorieBudget;
    }

    public void setCalorieBudget(int calorieBudget) {
        this.calorieBudget = calorieBudget;
    }

    public int getFatTargetG() {
        return fatTargetG;
    }

    public void setFatTargetG(int fatTargetG) {
        this.fatTargetG = fatTargetG;
    }

    public boolean isFoodsSeeded() {
        return foodsSeeded;
    }

    public void markFoodsSeeded() {
        this.foodsSeeded = true;
    }
}
