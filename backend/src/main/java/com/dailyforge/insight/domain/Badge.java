package com.dailyforge.insight.domain;

import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One badge in the catalogue: what it is called, what it takes, and what it pays.
 *
 * Seeded by migration and not user-editable, which is what separates a badge from a
 * goal. A goal is something you set for yourself; a badge is a fixed bar that means the
 * same thing for everyone, and it would mean nothing if you could lower it.
 */
@Entity
@Table(name = "badge")
public class Badge {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** What kind of badge this is, shown when it is opened. */
    @Column(name = "description", nullable = false, length = 300)
    private String description;

    /** What to do to earn it, in plain terms rather than as a formula. */
    @Column(name = "criteria", nullable = false, length = 300)
    private String criteria;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 10)
    private RecapPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 30)
    private BadgeMetric metric;

    @Column(name = "threshold", nullable = false)
    private int threshold;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Badge() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCriteria() {
        return criteria;
    }

    public RecapPeriod getPeriod() {
        return period;
    }

    public BadgeMetric getMetric() {
        return metric;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getPoints() {
        return points;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
