package com.dailyforge.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * The day boundary is where time-zone bugs live, so these tests use real zones and real
 * transitions rather than a convenient UTC fiction.
 */
class DayServiceTest {

    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private DayService at(String instant) {
        return new DayService(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
    }

    @Test
    void todayDiffersByZoneAtTheSameInstant() {
        // 2026-03-12T11:00Z is already the 13th in Auckland and still the 12th in LA.
        DayService day = at("2026-03-12T11:00:00Z");

        assertThat(day.today(AUCKLAND)).isEqualTo(LocalDate.of(2026, 3, 13));
        assertThat(day.today(LOS_ANGELES)).isEqualTo(LocalDate.of(2026, 3, 12));
    }

    @Test
    void aUserJustPastMidnightGetsTheNewDay() {
        // 11:30Z is 00:30 the next day in Auckland (UTC+13 in March).
        DayService day = at("2026-03-12T11:30:00Z");
        assertThat(day.today(AUCKLAND)).isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    void dayBoundsAreHalfOpen() {
        DayService day = at("2026-03-12T00:00:00Z");
        LocalDate date = LocalDate.of(2026, 3, 12);

        Instant start = day.startOfDay(date, LONDON);
        Instant end = day.endOfDayExclusive(date, LONDON);

        assertThat(start).isBefore(end);
        assertThat(day.toLocalDate(start, LONDON)).isEqualTo(date);
        // The exclusive bound already belongs to the next day, which is the point of it.
        assertThat(day.toLocalDate(end, LONDON)).isEqualTo(date.plusDays(1));
    }

    @Test
    void aDaylightSavingDayIsStillOneCalendarDay() {
        // Europe/London springs forward on 29 March 2026: that day is 23 hours long.
        DayService day = at("2026-03-29T12:00:00Z");
        LocalDate springForward = LocalDate.of(2026, 3, 29);

        Instant start = day.startOfDay(springForward, LONDON);
        Instant end = day.endOfDayExclusive(springForward, LONDON);

        assertThat(java.time.Duration.between(start, end).toHours()).isEqualTo(23);
        assertThat(day.toLocalDate(start, LONDON)).isEqualTo(springForward);
    }

    @Test
    void habitEditWindowCoversTodayAndYesterdayOnly() {
        DayService day = at("2026-03-12T10:00:00Z");
        LocalDate today = day.today(LONDON);

        assertThat(day.isWithinHabitEditWindow(today, LONDON)).isTrue();
        assertThat(day.isWithinHabitEditWindow(today.minusDays(1), LONDON)).isTrue();
        assertThat(day.isWithinHabitEditWindow(today.minusDays(2), LONDON)).isFalse();
        assertThat(day.isWithinHabitEditWindow(today.plusDays(1), LONDON)).isFalse();
    }

    @Test
    void generalEditWindowRespectsTheConfiguredLength() {
        DayService day = at("2026-03-12T10:00:00Z");
        LocalDate today = day.today(LONDON);

        assertThat(day.isWithinEditWindow(today.minusDays(7), LONDON, 7)).isTrue();
        assertThat(day.isWithinEditWindow(today.minusDays(8), LONDON, 7)).isFalse();
        assertThat(day.isWithinEditWindow(today.plusDays(1), LONDON, 7)).isFalse();
    }

    @Test
    void futureIsRelativeToTheUsersZone() {
        DayService day = at("2026-03-12T11:00:00Z");
        LocalDate thirteenth = LocalDate.of(2026, 3, 13);

        // Already today in Auckland, still tomorrow in Los Angeles.
        assertThat(day.isFuture(thirteenth, AUCKLAND)).isFalse();
        assertThat(day.isFuture(thirteenth, LOS_ANGELES)).isTrue();
    }

    @Test
    void daysBetweenIsSignedAndCountsWholeDays() {
        DayService day = at("2026-03-12T10:00:00Z");

        assertThat(day.daysBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12))).isEqualTo(11);
        assertThat(day.daysBetween(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 1))).isEqualTo(-11);
    }

    @Test
    void anUnknownZoneFallsBackToUtcRatherThanThrowing() {
        DayService day = at("2026-03-12T10:00:00Z");

        assertThat(day.zoneOf("Mars/Olympus_Mons")).isEqualTo(ZoneId.of("UTC"));
        assertThat(day.zoneOf("Europe/London")).isEqualTo(LONDON);
    }
}
