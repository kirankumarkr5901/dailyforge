package com.dailyforge.points.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One configured rule. {@code userId} null is the system default; a row with a
 * {@code userId} overrides it for that user (spec §5.4). No point value is ever
 * hardcoded in Java — everything reaches this table first.
 */
@Entity
@Table(name = "points_rule_config")
public class PointsRuleConfig {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "rule_code", nullable = false, length = 60)
    private String ruleCode;

    @Column(name = "config_json", nullable = false, length = 4000)
    private String configJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PointsRuleConfig() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getConfigJson() {
        return configJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }
}
