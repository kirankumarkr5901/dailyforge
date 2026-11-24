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
    @Column(name = "grams", nullable = false)
    private int grams;

    @Column(name = "kcal", nullable = false)
    private int kcal;

    @Column(name = "fat_g", nullable = false)
    private int fatG;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProteinLog() {
        // for JPA
    }

    public static ProteinLog create(UUID userId, LocalDate occurredOn, String foodName, int grams, int kcal, int fatG) {
        ProteinLog log = new ProteinLog();
        log.id = UUID.randomUUID();
        log.userId = userId;
        log.occurredOn = occurredOn;
        log.foodName = foodName;
        log.grams = grams;
        log.kcal = kcal;
        log.fatG = fatG;
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

    public int getGrams() {
        return grams;
    }

    public int getKcal() {
        return kcal;
    }

    public int getFatG() {
        return fatG;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
