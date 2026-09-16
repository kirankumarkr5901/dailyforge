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
