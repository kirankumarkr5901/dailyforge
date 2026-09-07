package com.dailyforge.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One spend (spec §8.9). {@code occurredOn} is what "undo within the same day" compares against, never {@code redeemedAt}. */
@Entity
@Table(name = "reward_redemption")
public class RewardRedemption {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reward_id", nullable = false)
    private UUID rewardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "points_spent", nullable = false)
    private int pointsSpent;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected RewardRedemption() {
        // for JPA
    }

    public static RewardRedemption create(UUID rewardId, UUID userId, LocalDate occurredOn, int pointsSpent) {
        RewardRedemption redemption = new RewardRedemption();
        redemption.id = UUID.randomUUID();
        redemption.rewardId = rewardId;
        redemption.userId = userId;
        redemption.occurredOn = occurredOn;
        redemption.pointsSpent = pointsSpent;
        return redemption;
    }

    public void refund(Instant when) {
        if (this.refundedAt == null) {
            this.refundedAt = when;
        }
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    @PrePersist
    void onCreate() {
        this.redeemedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRewardId() {
        return rewardId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public int getPointsSpent() {
        return pointsSpent;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
