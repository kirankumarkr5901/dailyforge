package com.dailyforge.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.UserDirectory;
import com.dailyforge.identity.domain.UserZone;
import com.dailyforge.points.domain.DailyRolloverJob;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.domain.RolloverParticipant;
import com.dailyforge.points.repo.UserRolloverStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec §5.6: runs hourly, settles every user whose local day has advanced, and "must be
 * idempotent — re-running it for the same date must produce no new entries" — which
 * here means no new calls to a participant, since no rule fires directly in this job.
 *
 * The clock and the user directory are both faked via a {@code @TestConfiguration}, so
 * the job's date-walking logic is exercised precisely without waiting on a real clock or
 * creating real accounts.
 */
@SpringBootTest
@Import(DailyRolloverJobTest.FakeClockConfig.class)
@ActiveProfiles("test")
class DailyRolloverJobTest {

    @Autowired private DailyRolloverJob job;
    @Autowired private FakeUserDirectory userDirectory;
    @Autowired private RolloverParticipant participant;
    @Autowired private UserRolloverStateRepository states;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void onlyTheUserWhoseDayHasClosedGetsTheParticipantCalled() {
        Mockito.reset(participant);
        java.util.UUID AUCKLAND_USER = TestUsers.create(users, settings);
        java.util.UUID LA_USER = TestUsers.create(users, settings);
        userDirectory.set(
                List.of(
                        new UserZone(AUCKLAND_USER, ZoneId.of("Pacific/Auckland")),
                        new UserZone(LA_USER, ZoneId.of("America/Los_Angeles"))));

        // 11:00Z: already 2026-03-13 in Auckland, still 2026-03-12 in LA.
        clock.set(Instant.parse("2026-03-12T11:00:00Z"));
        job.settleUser(new UserZone(AUCKLAND_USER, ZoneId.of("Pacific/Auckland")));
        job.settleUser(new UserZone(LA_USER, ZoneId.of("America/Los_Angeles")));

        // First run for both: no prior watermark, so nothing is "closed" yet — the
        // watermark simply initialises to yesterday, per user zone.
        verifyNoMoreInteractions(participant);
        assertThat(states.findById(AUCKLAND_USER).orElseThrow().getLastProcessedDate())
                .isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(states.findById(LA_USER).orElseThrow().getLastProcessedDate())
                .isEqualTo(LocalDate.of(2026, 3, 11));

        // Advance a day: Auckland's day closes again, LA's does too, one day apart.
        clock.set(Instant.parse("2026-03-13T11:00:00Z"));
        job.settleUser(new UserZone(AUCKLAND_USER, ZoneId.of("Pacific/Auckland")));
        job.settleUser(new UserZone(LA_USER, ZoneId.of("America/Los_Angeles")));

        verify(participant).onDayClosed(eq(AUCKLAND_USER), eq(LocalDate.of(2026, 3, 13)));
        verify(participant).onDayClosed(eq(LA_USER), eq(LocalDate.of(2026, 3, 12)));
        verifyNoMoreInteractions(participant);
    }

    @Test
    void aGapOfSeveralHoursSettlesEveryDateInBetweenNotOnlyTheLatest() {
        Mockito.reset(participant);
        java.util.UUID user = TestUsers.create(users, settings);
        UserZone userZone = new UserZone(user, ZoneId.of("UTC"));

        clock.set(Instant.parse("2026-03-10T01:00:00Z"));
        job.settleUser(userZone); // establishes the watermark at 2026-03-09

        // The host was asleep (docs/DEPLOYMENT.md phase 1b) and the job did not run again
        // until three days later.
        clock.set(Instant.parse("2026-03-13T01:00:00Z"));
        job.settleUser(userZone);

        InOrder order = Mockito.inOrder(participant);
        order.verify(participant).onDayClosed(user, LocalDate.of(2026, 3, 10));
        order.verify(participant).onDayClosed(user, LocalDate.of(2026, 3, 11));
        order.verify(participant).onDayClosed(user, LocalDate.of(2026, 3, 12));
        verifyNoMoreInteractions(participant);
    }

    @Test
    void runSettlesEveryActiveUserNotJustTheOneUnderTest() {
        Mockito.reset(participant);
        java.util.UUID userOne = TestUsers.create(users, settings);
        java.util.UUID userTwo = TestUsers.create(users, settings);
        userDirectory.set(
                List.of(new UserZone(userOne, ZoneId.of("UTC")), new UserZone(userTwo, ZoneId.of("UTC"))));

        clock.set(Instant.parse("2026-03-10T01:00:00Z"));
        job.run(); // the actual @Scheduled entry point, not settleUser called directly
        clock.set(Instant.parse("2026-03-11T01:00:00Z"));
        job.run();

        verify(participant).onDayClosed(userOne, LocalDate.of(2026, 3, 10));
        verify(participant).onDayClosed(userTwo, LocalDate.of(2026, 3, 10));
    }

    @Test
    void reRunningForTheSameMomentProducesNoFurtherParticipantCalls() {
        Mockito.reset(participant);
        java.util.UUID user = TestUsers.create(users, settings);
        UserZone userZone = new UserZone(user, ZoneId.of("UTC"));

        clock.set(Instant.parse("2026-03-10T01:00:00Z"));
        job.settleUser(userZone);
        clock.set(Instant.parse("2026-03-11T01:00:00Z"));
        job.settleUser(userZone);
        int callsAfterFirstAdvance = Mockito.mockingDetails(participant).getInvocations().size();

        // Running again at the exact same moment must be a true no-op.
        job.settleUser(userZone);
        int callsAfterRepeat = Mockito.mockingDetails(participant).getInvocations().size();

        assertThat(callsAfterRepeat).isEqualTo(callsAfterFirstAdvance);
    }

    static final class FakeUserDirectory implements UserDirectory {
        private volatile List<UserZone> users = List.of();

        void set(List<UserZone> users) {
            this.users = users;
        }

        @Override
        public List<UserZone> listActive() {
            return users;
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class FakeClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        DayService dayService(MutableClock clock) {
            return new DayService(clock);
        }

        @Bean
        @Primary
        FakeUserDirectory fakeUserDirectory() {
            return new FakeUserDirectory();
        }

        @Bean
        @Primary
        RolloverParticipant fakeParticipant() {
            return Mockito.mock(RolloverParticipant.class);
        }
    }
}
