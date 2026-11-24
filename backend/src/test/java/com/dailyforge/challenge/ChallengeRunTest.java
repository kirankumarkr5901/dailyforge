package com.dailyforge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.challenge.domain.ChallengeRun;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** "N days straight": a closed day without a tick restarts the run the day after it. */
class ChallengeRunTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    private static Set<LocalDate> done(int... dayNumbers) {
        Set<LocalDate> s = new HashSet<>();
        for (int n : dayNumbers) s.add(START.plusDays(n - 1L));
        return s;
    }

    private static LocalDate day(int n) {
        return START.plusDays(n - 1L);
    }

    @Test
    void everyDayDoneKeepsTheRunGoing() {
        var s = ChallengeRun.settle(START, day(4), done(1, 2, 3));
        assertThat(s.runStart()).isEqualTo(START);
        assertThat(s.dayNumber()).isEqualTo(4);
        assertThat(s.doneInARow()).isEqualTo(3);
        assertThat(s.restarted()).isFalse();
    }

    @Test
    void todayStillOpenNeverBreaksTheRunButCountsOnceTicked() {
        assertThat(ChallengeRun.settle(START, day(1), done()).restarted()).isFalse();
        assertThat(ChallengeRun.settle(START, day(1), done(1)).doneInARow()).isEqualTo(1);
    }

    @Test
    void aMissedDayRestartsTheRunTheDayAfterIt() {
        // Day 3 missed; days 4 and 5 done; today is day 6.
        var s = ChallengeRun.settle(START, day(6), done(1, 2, 4, 5));
        assertThat(s.restarted()).isTrue();
        assertThat(s.runStart()).isEqualTo(day(4));
        assertThat(s.dayNumber()).isEqualTo(3);
        assertThat(s.doneInARow()).isEqualTo(2);
    }

    @Test
    void comingBackAfterDaysAwayStartsAgainToday() {
        var s = ChallengeRun.settle(START, day(10), done(1, 2));
        assertThat(s.runStart()).isEqualTo(day(10));
        assertThat(s.dayNumber()).isEqualTo(1);
        assertThat(s.doneInARow()).isZero();
    }
}
