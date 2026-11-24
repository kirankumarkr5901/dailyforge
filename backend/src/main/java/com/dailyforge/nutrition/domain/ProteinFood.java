package com.dailyforge.nutrition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One of a user's one-tap foods: a name, and the protein, calories and fat a serving adds. */
@Entity
@Table(name = "protein_food")
public class ProteinFood {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Protein, in grams. */
    @Column(name = "grams", nullable = false)
    private int grams;

    @Column(name = "kcal", nullable = false)
    private int kcal;

    @Column(name = "fat_g", nullable = false)
    private int fatG;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProteinFood() {
        // for JPA
    }

    public static ProteinFood create(UUID userId, String name, int grams, int kcal, int fatG, int sortOrder) {
        ProteinFood food = new ProteinFood();
        food.id = UUID.randomUUID();
        food.userId = userId;
        food.name = name;
        food.grams = grams;
        food.kcal = kcal;
        food.fatG = fatG;
        food.sortOrder = sortOrder;
        return food;
    }

    public void update(String name, int grams, int kcal, int fatG) {
        this.name = name;
        this.grams = grams;
        this.kcal = kcal;
        this.fatG = fatG;
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

    public int getGrams() {
        return grams;
    }

    public int getKcal() {
        return kcal;
    }

    public int getFatG() {
        return fatG;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
