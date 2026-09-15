package com.dailyforge.habit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitBoardService;
import com.dailyforge.habit.domain.HabitDayState;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The board's state machine (spec §8.4) and the server-computed bonus hint (never client-side). */
@SpringBootTest
@Import(HabitClockTestConfig.class)
@ActiveProfiles("test")
class HabitBoardServiceTest {

    @Autowired private HabitService habitService;
    @Autowired private HabitLogService habitLogService;
    @Autowired private HabitBoardService board;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private HabitClockTestConfig.MutableClock clock;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void aDoneTodayShowsAsDoneAndIsStillEditable() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Habit habit = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        habitLogService.log(habit.getId(), user, TODAY);

        var entry = board.board(user, TODAY).habits().get(0);
        assertThat(entry.state()).isEqualTo(HabitDayState.DONE);
        assertThat(entry.editable()).isTrue();
    }

    @Test
    void anUnloggedTodayShowsAsPendingNotMissed() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        var entry = board.board(user, TODAY).habits().get(0);
        assertThat(entry.state()).isEqualTo(HabitDayState.PENDING);
    }

    @Test
    void anUnloggedPastDayShowsAsMissedAndIsLockedOnceOlderThanYesterday() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Habit habit = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY.minusDays(5));

        var entry = board.board(user, TODAY.minusDays(3)).habits().get(0);
        assertThat(entry.state()).isEqualTo(HabitDayState.MISSED);
        assertThat(entry.editable()).isFalse(); // older than yesterday

        var yesterdayEntry = board.board(user, TODAY.minusDays(1)).habits().get(0);
        assertThat(yesterdayEntry.state()).isEqualTo(HabitDayState.MISSED);
        assertThat(yesterdayEntry.editable()).isTrue(); // yesterday is still in the edit window
    }

    @Test
    void aFutureDayShowsAsPlannedWhetherOrNotItHasBeenPreTicked() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        Habit habit = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);
        LocalDate future = TODAY.plusDays(3);

        var beforeTick = board.board(user, future).habits().get(0);
        assertThat(beforeTick.state()).isEqualTo(HabitDayState.PLANNED);
        assertThat(beforeTick.plannedDone()).isFalse();

        habitLogService.log(habit.getId(), user, future);

        var afterTick = board.board(user, future).habits().get(0);
        assertThat(afterTick.state()).isEqualTo(HabitDayState.PLANNED); // still planned, not "earned"
        assertThat(afterTick.plannedDone()).isTrue();
    }

    @Test
    void aHabitNotScheduledOnTheRequestedDateIsExcludedFromTheBoard() {
        setToday(TODAY); // 2026-03-12 is a Thursday
        UUID user = TestUsers.create(users, settings);
        habitService.create(user, "Weekend run", "run", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 0b110_0000, TODAY);

        assertThat(board.board(user, TODAY).habits()).isEmpty();
    }

    @Test
    void theBonusHintNamesAHabitOneTickAwayFromASevenDayStreak() {
        setToday(TODAY.minusDays(6));
        UUID user = TestUsers.create(users, settings);
        Habit habit = habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY.minusDays(6));

        for (int i = 0; i < 6; i++) {
            LocalDate date = TODAY.minusDays(6).plusDays(i);
            setToday(date);
            habitLogService.log(habit.getId(), user, date);
        }
        setToday(TODAY); // day 7, not yet ticked

        var result = board.board(user, TODAY);
        assertThat(result.bonusHint()).isNotNull();
        assertThat(result.bonusHint()).contains("Read").contains("7-day streak").contains("+20");
    }

    @Test
    void thereIsNoBonusHintWhenNoHabitIsCloseToAThreshold() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        assertThat(board.board(user, TODAY).bonusHint()).isNull();
    }

    @Test
    void theHintOnlyEverConsidersTodayNeverAnotherViewedDate() {
        setToday(TODAY);
        UUID user = TestUsers.create(users, settings);
        habitService.create(user, "Read", "book", 10, HabitType.NORMAL, 0, 20, new BigDecimal("1.5"), 127, TODAY);

        // Viewing a future date must never surface a hint about "today ticking".
        assertThat(board.board(user, TODAY.plusDays(2)).bonusHint()).isNull();
    }
}
