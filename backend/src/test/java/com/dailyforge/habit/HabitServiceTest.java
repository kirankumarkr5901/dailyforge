package com.dailyforge.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.habit.repo.HabitRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(HabitClockTestConfig.class)
@ActiveProfiles("test")
class HabitServiceTest {

    @Autowired private HabitService habits;
    @Autowired private HabitLogService habitLogs;
    @Autowired private HabitRepository habitRepository;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private HabitClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void aHabitWithNoOverridesTakesItsBonusDefaultsFromConfiguration() {
        UUID user = TestUsers.create(users, settings);

        Habit habit = habits.create(user, "Read", "book", 10, HabitType.NORMAL, 0, null, null, 127, TODAY);

        // V2's seeded HABIT_CONSISTENCY defaults: baseBonus 20, multiplier 1.5.
        assertThat(habit.getBaseBonus()).isEqualTo(20);
        assertThat(habit.getBonusMultiplier()).isEqualByComparingTo("1.5");
    }

    @Test
    void aHabitCanOverrideItsOwnBonusDefaults() {
        UUID user = TestUsers.create(users, settings);

        Habit habit = habits.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 5, new BigDecimal("2.0"), 127, TODAY);

        assertThat(habit.getBaseBonus()).isEqualTo(5);
        assertThat(habit.getBonusMultiplier()).isEqualByComparingTo("2.0");
    }

    @Test
    void aNormalHabitAlwaysHasZeroPenaltyPointsEvenIfRequested() {
        UUID user = TestUsers.create(users, settings);

        Habit habit = habits.create(user, "Read", "book", 10, HabitType.NORMAL, 999, 20, new BigDecimal("1.5"), 127, TODAY);

        assertThat(habit.getPenaltyPoints()).isZero();
    }

    @Test
    void switchingFromStrictToNormalClearsThePenalty() {
        UUID user = TestUsers.create(users, settings);
        Habit habit = habits.create(user, "Cold shower", "drop", 10, HabitType.STRICT, 15, 20, new BigDecimal("1.5"), 127, TODAY);

        habits.update(habit.getId(), user, null, null, null, HabitType.NORMAL, null, null);

        Habit reloaded = habits.requireOwned(habit.getId(), user);
        assertThat(reloaded.getType()).isEqualTo(HabitType.NORMAL);
        assertThat(reloaded.getPenaltyPoints()).isZero();
    }

    @Test
    void reorderingSetsSortOrderToMatchTheGivenSequence() {
        UUID user = TestUsers.create(users, settings);
        Habit a = habits.create(user, "A", "x", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        Habit b = habits.create(user, "B", "x", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        Habit c = habits.create(user, "C", "x", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        habits.reorder(user, List.of(c.getId(), a.getId(), b.getId()));

        assertThat(habits.requireOwned(c.getId(), user).getSortOrder()).isZero();
        assertThat(habits.requireOwned(a.getId(), user).getSortOrder()).isEqualTo(1);
        assertThat(habits.requireOwned(b.getId(), user).getSortOrder()).isEqualTo(2);
    }

    @Test
    void aHabitWithNoLogsIsHardDeletedButOneWithLogsIsArchived() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Habit untouched = habits.create(user, "Untouched", "x", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        Habit logged = habits.create(user, "Logged", "x", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        habitLogs.log(logged.getId(), user, TODAY);

        habits.deleteOrArchive(untouched.getId(), user);
        habits.deleteOrArchive(logged.getId(), user);

        assertThat(habitRepository.findById(untouched.getId())).isEmpty();
        Habit stillThere = habitRepository.findById(logged.getId()).orElseThrow();
        assertThat(stillThere.isArchived()).isTrue();
    }

    @Test
    void anArchivedHabitDisappearsFromTheActiveListButNotFromHistory() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Habit habit = habits.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        habitLogs.log(habit.getId(), user, TODAY);

        habits.deleteOrArchive(habit.getId(), user);

        assertThat(habits.listActive(user)).isEmpty();
        assertThat(habitRepository.findByIdAndUserId(habit.getId(), user)).isPresent();
    }

    @Test
    void oneUserCannotReachAnotherUsersHabit() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        Habit habitOfA = habits.create(userA, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        assertThatThrownBy(() -> habits.requireOwned(habitOfA.getId(), userB)).isInstanceOf(ApiException.class);
    }

    @Test
    void isFirstHabitIsTrueOnlyBeforeAnyActiveHabitExists() {
        UUID user = TestUsers.create(users, settings);
        assertThat(habits.isFirstHabit(user)).isTrue();

        habits.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        assertThat(habits.isFirstHabit(user)).isFalse();
    }
}
