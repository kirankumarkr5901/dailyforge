package com.dailyforge.reward.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * How often a reward is meant to be earned (owner feedback): MICRO is a small daily
 * treat, WEEKLY and MONTHLY progressively bigger splurges — grouping the list so a
 * 5-point treat and a 2000-point reward do not sit undifferentiated together.
 *
 * <p>The tier also sets how often the reward's stock refreshes (owner request), which
 * is what finally makes the names true: a daily treat you could take three times ever
 * was not a daily treat. Stock is an allowance per period, and the period is this.
 */
public enum RewardTier {
    MICRO("today", "day"),
    WEEKLY("this week", "week"),
    MONTHLY("this month", "month");

    private final String windowLabel;
    private final String periodNoun;

    RewardTier(String windowLabel, String periodNoun) {
        this.windowLabel = windowLabel;
        this.periodNoun = periodNoun;
    }

    /** "today" / "this week" / "this month" — for "2 left today". */
    public String windowLabel() {
        return windowLabel;
    }

    /** "day" / "week" / "month" — for "refreshes every day". */
    public String periodNoun() {
        return periodNoun;
    }

    /**
     * The first day of the period {@code today} falls in.
     *
     * <p>Weeks start on Monday, the same as the score snapshot's own "this week" and the
     * body metrics' weekly streak. Two different week boundaries in one app would have
     * the reward list and the score card disagreeing about which week it is.
     */
    public LocalDate periodStart(LocalDate today) {
        return switch (this) {
            case MICRO -> today;
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> today.withDayOfMonth(1);
        };
    }

    /** The last day of that period — inclusive, so a same-day window is start == end. */
    public LocalDate periodEnd(LocalDate today) {
        return switch (this) {
            case MICRO -> today;
            case WEEKLY -> periodStart(today).plusDays(6);
            case MONTHLY -> today.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    /** The day the allowance next comes back. */
    public LocalDate nextRefresh(LocalDate today) {
        return periodEnd(today).plusDays(1);
    }
}
