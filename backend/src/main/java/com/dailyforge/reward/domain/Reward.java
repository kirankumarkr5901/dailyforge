package com.dailyforge.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Something points can be spent on (spec §8.9). */
@Entity
@Table(name = "reward")
public class Reward {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "cost", nullable = false)
    private int cost;

    @Column(name = "icon", nullable = false, length = 40)
    private String icon;

    @Column(name = "is_repeatable", nullable = false)
    private boolean repeatable;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reward() {
        // for JPA
    }

    public static Reward create(UUID userId, String name, int cost, String icon, boolean repeatable, Integer stock) {
        Reward reward = new Reward();
        reward.id = UUID.randomUUID();
        reward.userId = userId;
        reward.name = name;
        reward.cost = cost;
        reward.icon = icon;
        reward.repeatable = repeatable;
        reward.stock = stock;
        return reward;
    }

    public void decrementStock() {
        if (stock != null) {
            stock = Math.max(0, stock - 1);
        }
    }

    public void incrementStock() {
        if (stock != null) {
            stock = stock + 1;
        }
    }

    public void archive(Instant when) {
        if (this.archivedAt == null) {
            this.archivedAt = when;
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isOutOfStock() {
        return stock != null && stock <= 0;
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

    public int getCost() {
        return cost;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public Integer getStock() {
        return stock;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
