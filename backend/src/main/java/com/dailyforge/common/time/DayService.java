package com.dailyforge.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/**
 * The single owner of "what day is it".
 *
 * Every streak, heatmap cell, daily bonus and "today's points" resolves through here, in
 * the user's own time zone (spec §4.2). {@code LocalDate.now()} appears nowhere else in
 * the codebase, because anywhere else it would silently answer in the server's zone —
 * which is how a user in Auckland loses a streak to a server in Virginia.
 *
 * The {@link Clock} is injected so tests can move time without sleeping.
 */
@Service
public class DayService {

    private final Clock clock;

    public DayService(Clock clock) {
        this.clock = clock;
    }

    /** The user's current local date. */
    public LocalDate today(ZoneId userZone) {
        return LocalDate.ofInstant(clock.instant(), userZone);
    }

    /** The local date an instant falls on, for that user. */
    public LocalDate toLocalDate(Instant instant, ZoneId userZone) {
        return LocalDate.ofInstant(instant, userZone);
    }

    /** The instant a user's local day begins — the inclusive lower bound of that day. */
    public Instant startOfDay(LocalDate date, ZoneId userZone) {
        return date.atStartOfDay(userZone).toInstant();
    }

    /** The instant the next local day begins — the exclusive upper bound. */
    public Instant endOfDayExclusive(LocalDate date, ZoneId userZone) {
        return date.plusDays(1).atStartOfDay(userZone).toInstant();
    }

    /** True when {@code date} is the user's today or yesterday — the habit edit window. */
    public boolean isWithinHabitEditWindow(LocalDate date, ZoneId userZone) {
        LocalDate today = today(userZone);
        return !date.isAfter(today) && !date.isBefore(today.minusDays(1));
    }

    /**
     * True when {@code date} is within the general edit window for workouts, runs,
     * activities and body metrics.
     *
     * @param windowDays how many days back remain editable; comes from configuration,
     *                   defaulting to 7 (spec §4.3), never hardcoded at a call site.
     */
    public boolean isWithinEditWindow(LocalDate date, ZoneId userZone, int windowDays) {
        LocalDate today = today(userZone);
        return !date.isAfter(today) && !date.isBefore(today.minusDays(windowDays));
    }

    /** True when the date has not arrived yet for this user. Future habit logs are allowed. */
    public boolean isFuture(LocalDate date, ZoneId userZone) {
        return date.isAfter(today(userZone));
    }

    /** Whole days between two local dates, negative when {@code to} is earlier. */
    public long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    /** Parses a stored IANA zone id, falling back to UTC rather than throwing at read time. */
    public ZoneId zoneOf(String ianaZoneId) {
        try {
            return ZoneId.of(ianaZoneId);
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }
}
