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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** LIST, PHOTO or BARCODE: where the food came from. */
    @Column(name = "source", nullable = false, length = 10)
    private String source = SOURCE_LIST;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static final String SOURCE_LIST = "LIST";
    public static final java.util.Set<String> SOURCES = java.util.Set.of(SOURCE_LIST, "PHOTO", "BARCODE");

    protected ProteinFood() {
        // for JPA
    }

    public static ProteinFood create(UUID userId, String name, double grams, int kcal, double fatG, int sortOrder) {
        ProteinFood food = new ProteinFood();
        food.id = UUID.randomUUID();
        food.userId = userId;
        food.name = name;
        food.grams = oneDecimal(grams);
        food.kcal = kcal;
        food.fatG = oneDecimal(fatG);
        food.sortOrder = sortOrder;
        return food;
    }

    public void update(String name, double grams, int kcal, double fatG) {
        this.name = name;
        this.grams = oneDecimal(grams);
        this.kcal = kcal;
        this.fatG = oneDecimal(fatG);
    }

    public void updateMore(double carbsG, double fiberG, double sugarG) {
        this.carbsG = oneDecimal(carbsG);
        this.fiberG = oneDecimal(fiberG);
        this.sugarG = oneDecimal(sugarG);
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

    public int getSortOrder() {
        return sortOrder;
    }

    public String getSource() {
        return source;
    }

    void setSource(String source) {
        this.source = source;
    }

    /** Grams keep one decimal (owner request): 9.4 g stays 9.4 g. */
    static java.math.BigDecimal oneDecimal(double v) {
        return java.math.BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
