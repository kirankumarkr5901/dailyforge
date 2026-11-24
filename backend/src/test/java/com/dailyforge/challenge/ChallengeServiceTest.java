package com.dailyforge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.challenge.domain.ChallengeService;
import com.dailyforge.challenge.domain.ChallengeService.Status;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.forge.ForgeClockTestConfig;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** A challenge end to end, with the day moved forward by a test clock. */
@SpringBootTest
@Import(ForgeClockTestConfig.class)
@ActiveProfiles("test")
class ChallengeServiceTest {

    @Autowired private ChallengeService challenges;
    @Autowired private HabitLogService habitLogs;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private ForgeClockTestConfig.MutableClock clock;

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 5, 4);

    private UUID user;

    @BeforeEach
    void setUp() {
        setToday(DAY_ONE);
        user = TestUsers.create(users, settings);
    }

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private ChallengeService.View only() {
        return challenges.list(user).get(0);
    }

    private void tickOn(int dayNumber, UUID habitId) {
        LocalDate d = DAY_ONE.plusDays(dayNumber - 1L);
        setToday(d);
        habitLogs.log(habitId, user, d);
    }

    @Test
    void ticksBuildTheRunAndReachingTheLengthWaitsForAClaim() {
        var c = challenges.create(user, "Plank", "trophy", 10, 3, 150);
        assertThat(c.dayNumber()).isEqualTo(1);
        assertThat(c.status()).isEqualTo(Status.ACTIVE);

        tickOn(1, c.habitId());
        tickOn(2, c.habitId());
        assertThat(only().doneInARow()).isEqualTo(2);
        tickOn(3, c.habitId());

        var reached = only();
        assertThat(reached.status()).isEqualTo(Status.REACHED);
        assertThat(reached.doneInARow()).isEqualTo(3);

        // A miss after reaching it does not take it away.
        setToday(DAY_ONE.plusDays(5));
        assertThat(only().status()).isEqualTo(Status.REACHED);

        var claim = challenges.claim(user, c.id());
        assertThat(claim.points().delta()).isEqualTo(150);
        assertThat(claim.challenge().status()).isEqualTo(Status.COMPLETED);
        assertThatThrownBy(() -> challenges.claim(user, c.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void aMissedDayRestartsAtDayOne() {
        var c = challenges.create(user, "Plank", "trophy", 10, 30, 100);
        tickOn(1, c.habitId());
        tickOn(2, c.habitId());

        // Day 3 is missed; the app is opened on day 4.
        setToday(DAY_ONE.plusDays(3));
        var after = only();

        assertThat(after.dayNumber()).isEqualTo(1);
        assertThat(after.doneInARow()).isZero();
        assertThat(after.restarts()).isEqualTo(1);
        assertThat(after.runStart()).isEqualTo(DAY_ONE.plusDays(3));
        // Reading again settles nothing new.
        assertThat(only().restarts()).isEqualTo(1);
    }

    @Test
    void claimingBeforeTheEndIsRefusedAndAbandoningFreezesIt() {
        var c = challenges.create(user, "Plank", "trophy", 10, 30, 100);
        assertThatThrownBy(() -> challenges.claim(user, c.id())).isInstanceOf(ApiException.class);

        assertThat(challenges.abandon(user, c.id()).status()).isEqualTo(Status.ABANDONED);
    }

    @Test
    void anotherUsersChallengeIsNotFound() {
        var c = challenges.create(user, "Plank", "trophy", 10, 30, 100);
        UUID stranger = TestUsers.create(users, settings);

        assertThatThrownBy(() -> challenges.claim(stranger, c.id())).isInstanceOf(ApiException.class);
    }
}
