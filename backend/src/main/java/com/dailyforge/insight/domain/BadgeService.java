package com.dailyforge.insight.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.insight.domain.MilestoneService.Recap;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import com.dailyforge.insight.repo.BadgeAwardRepository;
import com.dailyforge.insight.repo.BadgeRepository;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Badges: a threshold over a recap period, with a name and a payout.
 *
 * <p>Badges deliberately invent no tracking of their own. Every figure one is scored
 * against already exists on {@link Recap} — a badge that counted something for itself
 * could disagree with the recap printed directly above it on the same page, and the
 * user would have no way to tell which was lying.
 *
 * <p>Earning and claiming are separate on purpose (owner's choice). Crossing the
 * threshold makes a badge claimable; the points arrive when the user takes them. A
 * payout that landed silently would be a number that moved on its own, and the whole
 * point of a badge is that it is a moment.
 */
@Service
public class BadgeService {

    /** The rule every badge payout is written under; the amount comes from the badge row. */
    private static final String RULE_CODE = "BADGE_CLAIM";

    private static final String SOURCE_TYPE = "BADGE";

    private final BadgeRepository badges;
    private final BadgeAwardRepository awards;
    private final MilestoneService milestones;
    private final com.dailyforge.points.domain.PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public BadgeService(
            BadgeRepository badges,
            BadgeAwardRepository awards,
            MilestoneService milestones,
            com.dailyforge.points.domain.PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.badges = badges;
        this.awards = awards;
        this.milestones = milestones;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    /**
     * One badge as the user sees it: the definition, where they have got to, and whether
     * there is anything to do about it.
     */
    public record BadgeProgress(
            Badge badge,
            LocalDate periodStart,
            LocalDate periodEnd,
            long value,
            boolean earned,
            boolean claimed,
            Instant claimedAt) {

        /** The only state with an action attached: earned, and not yet taken. */
        public boolean claimable() {
            return earned && !claimed;
        }
    }

    /** Every badge for a period, in catalogue order, with this user's progress against it. */
    @Transactional(readOnly = true)
    public List<BadgeProgress> progress(UUID userId, RecapPeriod period, LocalDate anchor) {
        Recap recap = milestones.recap(userId, period, anchor);
        LocalDate periodStart = recap.startDate();
        LocalDate periodEnd = periodEndOf(period, periodStart);

        Map<String, BadgeAward> claimed =
                awards.findAllByUserIdAndPeriodStart(userId, periodStart).stream()
                        .collect(Collectors.toMap(BadgeAward::getBadgeCode, Function.identity()));

        return badges.findAllByPeriodAndActiveTrueOrderBySortOrderAsc(period).stream()
                .map(
                        badge -> {
                            long value = valueOf(recap, badge.getMetric());
                            BadgeAward award = claimed.get(badge.getCode());
                            return new BadgeProgress(
                                    badge,
                                    periodStart,
                                    periodEnd,
                                    value,
                                    value >= badge.getThreshold(),
                                    award != null,
                                    award != null ? award.getClaimedAt() : null);
                        })
                .toList();
    }

    /** What a claim gives back: the badge as it now stands, and the points that moved. */
    public record ClaimResult(BadgeProgress badge, PointsResult points) {}

    /**
     * Take the points for a badge that has been earned.
     *
     * <p>Refused, rather than silently ignored, in three cases: the badge does not exist,
     * it has not been earned in this period, or it has already been claimed for this
     * period. Each is a different mistake and each gets its own message — "nothing
     * happened" would leave the user tapping a button that does nothing.
     *
     * <p>Guarded twice against a double claim. The unique constraint on
     * {@code (user_id, badge_code, period_start)} is the real defence, because it holds
     * across two requests that both passed the check at the same instant; the check is
     * there to produce a sensible message in the ordinary case. The points award carries
     * a deterministic idempotency key for the same reason, so even a retry that somehow
     * got past both cannot pay twice.
     */
    @Transactional
    public ClaimResult claim(UUID userId, String code, LocalDate anchor) {
        Badge badge =
                badges.findByCodeAndActiveTrue(code)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "That badge does not exist."));

        Recap recap = milestones.recap(userId, badge.getPeriod(), anchor);
        LocalDate periodStart = recap.startDate();
        LocalDate periodEnd = periodEndOf(badge.getPeriod(), periodStart);
        long value = valueOf(recap, badge.getMetric());

        if (value < badge.getThreshold()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    HttpStatus.CONFLICT,
                    "You have not earned " + badge.getName() + " yet. " + badge.getCriteria());
        }

        if (awards.findByUserIdAndBadgeCodeAndPeriodStart(userId, code, periodStart).isPresent()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.CONFLICT, "You have already claimed " + badge.getName() + " for this period.");
        }

        // The payout is the badge's own, never a literal here (spec §5.4). Reading the
        // rule config also means a disabled BADGE_CLAIM row switches badge payouts off
        // without a deploy, the same as every other rule.
        if (!ruleConfigs.getSystemDefault(RULE_CODE).enabled()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Badge points are switched off.");
        }

        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        BadgeAward award =
                BadgeAward.create(userId, code, periodStart, periodEnd, badge.getPoints(), Instant.now());
        try {
            awards.saveAndFlush(award);
        } catch (DataIntegrityViolationException raced) {
            // Two claims arrived together and this one lost. The winner already paid.
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.CONFLICT, "You have already claimed " + badge.getName() + " for this period.");
        }

        PointsResult result =
                points.award(
                        new AwardCommand(
                                userId,
                                dayService.today(zone),
                                PointsCategory.BADGE,
                                RULE_CODE,
                                badge.getPoints(),
                                SOURCE_TYPE,
                                award.getId(),
                                badge.getName() + " — " + periodLabel(badge.getPeriod(), periodStart),
                                "badge:" + code + ":" + periodStart));

        return new ClaimResult(
                new BadgeProgress(badge, periodStart, periodEnd, value, true, true, award.getClaimedAt()), result);
    }

    /** Every badge this user has ever claimed, newest first — the trophy shelf. */
    @Transactional(readOnly = true)
    public List<BadgeAward> claimed(UUID userId) {
        return awards.findAllByUserIdOrderByClaimedAtDesc(userId);
    }

    /**
     * The true end of the period, not the recap's clamped one.
     *
     * A recap for an open month stops at today, which is right for counting but wrong
     * for recording what period an award belongs to: a badge claimed on the 3rd of March
     * is March's badge, not a badge for the 1st to the 3rd.
     */
    private LocalDate periodEndOf(RecapPeriod period, LocalDate periodStart) {
        return period == RecapPeriod.YEAR
                ? periodStart.with(TemporalAdjusters.lastDayOfYear())
                : periodStart.with(TemporalAdjusters.lastDayOfMonth());
    }

    private String periodLabel(RecapPeriod period, LocalDate periodStart) {
        return period == RecapPeriod.YEAR
                ? String.valueOf(periodStart.getYear())
                : periodStart.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                        + " "
                        + periodStart.getYear();
    }

    private long valueOf(Recap recap, BadgeMetric metric) {
        return switch (metric) {
            case TOTAL_POINTS -> recap.totalPoints();
            case WORKOUT_DAYS -> recap.workoutDays();
            case RUN_DAYS -> recap.runDays();
            case RUN_DISTANCE_METERS -> recap.runDistanceMeters();
            case HABITS_COMPLETED -> recap.habitsCompleted();
            case GOALS_COMPLETED -> recap.goalsCompleted();
        };
    }
}
