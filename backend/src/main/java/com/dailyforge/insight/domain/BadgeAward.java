package com.dailyforge.insight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A badge a user has claimed, for one period.
 *
 * {@code periodStart} is what makes a badge repeatable without being repeatable twice.
 * The same badge can be earned every month; the unique constraint on
 * {@code (user_id, badge_code, period_start)} is what stops March's being claimed again
 * while still allowing April's.
 *
 * {@code pointsAwarded} is recorded rather than read back from the catalogue, because
 * the catalogue can be rebalanced later and what was actually paid at the time should
 * not silently change with it.
 */
@Entity
@Table(name = "badge_award")
public class BadgeAward {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "badge_code", nullable = false, length = 40)
    private String badgeCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    protected BadgeAward() {
        // for JPA
    }

    public static BadgeAward create(
            UUID userId, String badgeCode, LocalDate periodStart, LocalDate periodEnd, int pointsAwarded, Instant claimedAt) {
        BadgeAward award = new BadgeAward();
        award.id = UUID.randomUUID();
        award.userId = userId;
        award.badgeCode = badgeCode;
        award.periodStart = periodStart;
        award.periodEnd = periodEnd;
        award.pointsAwarded = pointsAwarded;
        award.claimedAt = claimedAt;
        return award;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }
}
