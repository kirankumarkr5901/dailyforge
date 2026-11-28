package com.dailyforge.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One tap on the protein meter. The food's name and grams are copied in, not linked,
 * so editing or deleting a food later never changes what a past day added up to.
 */
@Entity
@Table(name = "protein_log")
public class ProteinLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "food_name", nullable = false, length = 60)
    private String foodName;

    /** Protein, in grams. */
    @Column(name = "grams", nullable = false, precision = 6, scale = 1)
    private java.math.BigDecimal grams = java.math.BigDecimal.ZERO;

    @Column(name = "kcal", nullable = false)
    private int kcal;

    @Column(name = "fat_g", nullable = false, precision = 6, scale = 1)
    private java.math.BigDecimal fatG = java.math.BigDecimal.ZERO;

    @Column(name = "carbs_g", nullable = false, precision = 6, scale = 1)
    private java.math.BigDecimal carbsG = java.math.BigDecimal.ZERO;

    @Column(name = "fiber_g", nullable = false, precision = 6, scale = 1)
    private java.math.BigDecimal fiberG = java.math.BigDecimal.ZERO;

    @Column(name = "sugar_g", nullable = false, precision = 6, scale = 1)
    private java.math.BigDecimal sugarG = java.math.BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProteinLog() {
        // for JPA
    }

    public static ProteinLog create(UUID userId, LocalDate occurredOn, String foodName, double grams, int kcal, double fatG) {
        ProteinLog log = new ProteinLog();
        log.id = UUID.randomUUID();
        log.userId = userId;
        log.occurredOn = occurredOn;
        log.foodName = foodName;
        log.grams = oneDecimal(grams);
        log.kcal = kcal;
        log.fatG = oneDecimal(fatG);
        return log;
    }

    public static ProteinLog create(
            UUID userId, LocalDate occurredOn, String foodName, double grams, int kcal, double fatG, double carbsG, double fiberG, double sugarG) {
        ProteinLog log = create(userId, occurredOn, foodName, grams, kcal, fatG);
        log.carbsG = oneDecimal(carbsG);
        log.fiberG = oneDecimal(fiberG);
        log.sugarG = oneDecimal(sugarG);
        return log;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
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

    public String getFoodName() {
        return foodName;
    }

    public double getGrams() {
        return grams.doubleValue();
    }

    public int getKcal() {
        return kcal;
    }

    public double getFatG() {
        return fatG.doubleValue();
    }

    public double getCarbsG() {
        return carbsG.doubleValue();
    }

    public double getFiberG() {
        return fiberG.doubleValue();
    }

    public double getSugarG() {
        return sugarG.doubleValue();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Grams keep one decimal (owner request): 9.4 g stays 9.4 g. */
    static java.math.BigDecimal oneDecimal(double v) {
        return java.math.BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
