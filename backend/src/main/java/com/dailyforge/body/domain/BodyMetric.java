package com.dailyforge.body.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One weight log. No points anywhere in this module (spec §8.8). */
@Entity
@Table(name = "body_metric")
public class BodyMetric {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(name = "note", length = 280)
    private String note;

    // Tape measurements, centimetres, all optional (V37).
    @Column(name = "waist_cm", precision = 5, scale = 1)
    private BigDecimal waistCm;

    @Column(name = "chest_cm", precision = 5, scale = 1)
    private BigDecimal chestCm;

    @Column(name = "arm_cm", precision = 5, scale = 1)
    private BigDecimal armCm;

    @Column(name = "hips_cm", precision = 5, scale = 1)
    private BigDecimal hipsCm;

    @Column(name = "thigh_cm", precision = 5, scale = 1)
    private BigDecimal thighCm;

    /** The five measurements together; any of them may be null. */
    public record Measurements(BigDecimal waistCm, BigDecimal chestCm, BigDecimal armCm, BigDecimal hipsCm, BigDecimal thighCm) {
        public static final Measurements NONE = new Measurements(null, null, null, null, null);
    }

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BodyMetric() {
        // for JPA
    }

    public static BodyMetric create(
            UUID userId, LocalDate occurredOn, BigDecimal weightKg, BigDecimal heightCm, BigDecimal bodyFatPct, String note) {
        BodyMetric metric = new BodyMetric();
        metric.id = UUID.randomUUID();
        metric.userId = userId;
        metric.occurredOn = occurredOn;
        metric.apply(weightKg, heightCm, bodyFatPct, note);
        return metric;
    }

    public void apply(BigDecimal weightKg, BigDecimal heightCm, BigDecimal bodyFatPct, String note) {
        this.weightKg = weightKg;
        this.heightCm = heightCm;
        this.bodyFatPct = bodyFatPct;
        this.note = note;
    }

    /**
     * Sets the measurements given; a null one keeps what the day already had, so logging
     * the weight again later that day never wipes a waist measured that morning.
     */
    public void applyMeasurements(Measurements m) {
        if (m.waistCm() != null) this.waistCm = m.waistCm();
        if (m.chestCm() != null) this.chestCm = m.chestCm();
        if (m.armCm() != null) this.armCm = m.armCm();
        if (m.hipsCm() != null) this.hipsCm = m.hipsCm();
        if (m.thighCm() != null) this.thighCm = m.thighCm();
    }

    public Measurements getMeasurements() {
        return new Measurements(waistCm, chestCm, armCm, hipsCm, thighCm);
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

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }

    public String getNote() {
        return note;
    }
}
