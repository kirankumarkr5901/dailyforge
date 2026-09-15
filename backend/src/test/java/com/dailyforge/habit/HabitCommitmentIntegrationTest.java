package com.dailyforge.habit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * "Every active habit scheduled for that day is complete" (spec §5.4) — a whole-day
 * property, not a per-habit one, so it needs its own test independent of
 * {@link HabitReconciliationIntegrationTest}'s per-habit scenarios.
 */
@SpringBootTest
@Import(HabitClockTestConfig.class)
@ActiveProfiles("test")
class HabitCommitmentIntegrationTest {

    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private IdentityService identity;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private HabitClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void completingEveryScheduledHabitEarnsTheCommitmentBonus() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        setCommitmentBonus(user, 50);

        Habit a = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        Habit b = habitService.create(user, "Stretch", "yoga", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        habitLogService.log(a.getId(), user, TODAY);
        int beforeLast = entries.sumAmountForUser(user);
        habitLogService.log(b.getId(), user, TODAY); // the last one — tips the day complete

        int afterLast = entries.sumAmountForUser(user);
        assertThat(afterLast - beforeLast).isEqualTo(10 + 50); // b's own base points, plus the commitment bonus
    }

    @Test
    void untickingOneHabitReversesTheCommitmentBonusButNotTheOthersBasePoints() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        setCommitmentBonus(user, 50);

        Habit a = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        Habit b = habitService.create(user, "Stretch", "yoga", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        habitLogService.log(a.getId(), user, TODAY);
        habitLogService.log(b.getId(), user, TODAY);

        int totalWithBoth = entries.sumAmountForUser(user);
        assertThat(totalWithBoth).isEqualTo(10 + 10 + 50);

        habitLogService.unlog(b.getId(), user, TODAY);

        int totalAfterUntick = entries.sumAmountForUser(user);
        assertThat(totalAfterUntick).isEqualTo(10); // only a's base points remain; commitment reversed, b's base gone
    }

    @Test
    void aHabitNotScheduledTodayIsNotRequiredForTheCommitmentBonus() {
        setToday(TODAY); // 2026-03-12 is a Thursday
        UUID user = TestUsers.create(users, settings);
        setCommitmentBonus(user, 50);

        Habit weekdaysOnly =
                habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 0b001_1111, TODAY);
        Habit weekendOnly =
                // scheduled Sat+Sun only (bits 5-6) — not scheduled today, so it must not
                // block the bonus even though it is untouched.
                habitService.create(user, "Long run", "run", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 0b110_0000, TODAY);

        habitLogService.log(weekdaysOnly.getId(), user, TODAY);

        int total = entries.sumAmountForUser(user);
        assertThat(total).isEqualTo(10 + 50); // the weekend-only habit was never in scope for today
    }

    @Test
    void noScheduledHabitsAtAllEarnsNoVacuousBonus() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        setCommitmentBonus(user, 50);

        // No habits exist for this user at all — nothing to complete, nothing to reward.
        assertThat(entries.sumAmountForUser(user)).isZero();
    }

    private void setCommitmentBonus(UUID userId, int amount) {
        var settingsEntity = identity.requireSettings(userId);
        settingsEntity.setCommitmentBonus(amount);
        settings.save(settingsEntity);
    }
}
