package com.dailyforge.points;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.domain.ReconciliationCalculator;
import com.dailyforge.points.repo.PointsEntryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec §5.3: "This must be covered by property-based tests: apply a random sequence of
 * log/unlog/edit operations, then assert that the ledger sum equals a naive
 * from-scratch recomputation. This test is non-negotiable."
 *
 * No module owns a real {@link ReconcileScope} yet — habits arrive at M3 — so this
 * proves the mechanism itself: a synthetic calculator stands in for "a habit's raw
 * logs", backed by nothing but an in-memory set of ticked dates, exactly like a real
 * habit's log table would be. Random log, unlog and edit (re-tick with a different
 * amount) operations run against it, {@code reconcile()} runs after every one, and the
 * ledger is checked against an independent naive recomputation each time.
 *
 * When M3 builds the real habit module, it writes a real
 * {@link ReconciliationCalculator} against this same, already-proven engine — this test
 * does not get rewritten, it gets a sibling.
 *
 * The fake calculator is registered through a nested {@code @TestConfiguration} rather
 * than a {@code @Component}, so it exists only inside this test class's Spring context
 * and never leaks into any other test in the suite.
 */
@SpringBootTest
@Import(ReconciliationEnginePropertyTest.FakeHabitReconciliationConfig.class)
@ActiveProfiles("test")
class ReconciliationEnginePropertyTest {

    @Autowired private PointsService points;
    @Autowired private PointsEntryRepository entries;
    @Autowired private FakeHabitLog fakeHabitLog;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final String RULE_CODE = "TEST_HABIT_TICK";
    private static final String SOURCE_TYPE = "TEST_HABIT_LOG";

    @RepeatedTest(20)
    void aRandomSequenceOfTickUntickAndAmountEditsAlwaysLeavesTheLedgerMatchingANaiveRecompute() {
        UUID userId = TestUsers.create(users, settings);
        UUID habitId = UUID.randomUUID();
        fakeHabitLog.reset();

        Random random = new Random();
        List<LocalDate> candidateDates =
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> LocalDate.of(2026, 1, 1).plusDays(i))
                        .toList();

        for (int step = 0; step < 60; step++) {
            LocalDate date = candidateDates.get(random.nextInt(candidateDates.size()));

            switch (random.nextInt(3)) {
                case 0 -> fakeHabitLog.tick(habitId, date, 10); // log
                case 1 -> fakeHabitLog.untick(habitId, date); // unlog
                default -> fakeHabitLog.tick(habitId, date, 15); // edit: re-tick at a new amount
            }

            points.reconcile(userId, new ReconcileScope.Habit(habitId, candidateDates.getFirst()));

            int ledgerSum = entries.sumAmountForUser(userId);
            int naiveSum = fakeHabitLog.naiveTotal(habitId);

            assertThat(ledgerSum)
                    .as("after step %d (%s on %s), ledger must equal a from-scratch recompute", step, date, date)
                    .isEqualTo(naiveSum);
        }
    }

    @Test
    void reconcilingTwiceInARowWithNoChangeWritesNoNewEntries() {
        UUID userId = TestUsers.create(users, settings);
        UUID habitId = UUID.randomUUID();
        fakeHabitLog.reset();
        fakeHabitLog.tick(habitId, LocalDate.of(2026, 1, 1), 10);

        points.reconcile(userId, new ReconcileScope.Habit(habitId, LocalDate.of(2026, 1, 1)));
        int countAfterFirst = entries.countForUser(userId);

        points.reconcile(userId, new ReconcileScope.Habit(habitId, LocalDate.of(2026, 1, 1)));
        int countAfterSecond = entries.countForUser(userId);

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void editingTheAmountReversesTheOldEntryAndAwardsTheNewOneRatherThanEditingInPlace() {
        UUID userId = TestUsers.create(users, settings);
        UUID habitId = UUID.randomUUID();
        fakeHabitLog.reset();
        fakeHabitLog.tick(habitId, LocalDate.of(2026, 1, 1), 10);
        points.reconcile(userId, new ReconcileScope.Habit(habitId, LocalDate.of(2026, 1, 1)));

        fakeHabitLog.tick(habitId, LocalDate.of(2026, 1, 1), 25); // the habit's points value changed
        points.reconcile(userId, new ReconcileScope.Habit(habitId, LocalDate.of(2026, 1, 1)));

        // Two rows for that date: the original +10 (now reversed) and its -10 reversal,
        // plus the new +25 — never an in-place edit of the first row's amount.
        assertThat(entries.sumAmountForUser(userId)).isEqualTo(25);
        assertThat(entries.countForUser(userId)).isEqualTo(3);
    }

    /** A stand-in for a habit's raw log table: which dates are ticked, and at what amount. */
    static final class FakeHabitLog {
        private final java.util.Map<UUID, java.util.Map<LocalDate, Integer>> byHabit = new java.util.HashMap<>();

        void reset() {
            byHabit.clear();
        }

        void tick(UUID habitId, LocalDate date, int amount) {
            byHabit.computeIfAbsent(habitId, k -> new java.util.HashMap<>()).put(date, amount);
        }

        void untick(UUID habitId, LocalDate date) {
            byHabit.getOrDefault(habitId, java.util.Map.of()).keySet().remove(date);
        }

        java.util.Map<LocalDate, Integer> tickedDatesFrom(UUID habitId, LocalDate fromDate) {
            return byHabit.getOrDefault(habitId, java.util.Map.of()).entrySet().stream()
                    .filter(e -> !e.getKey().isBefore(fromDate))
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
        }

        /** The independent recomputation the ledger is checked against. */
        int naiveTotal(UUID habitId) {
            return byHabit.getOrDefault(habitId, java.util.Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static final class FakeHabitCalculator implements ReconciliationCalculator<ReconcileScope.Habit> {

        private final FakeHabitLog log;

        FakeHabitCalculator(FakeHabitLog log) {
            this.log = log;
        }

        @Override
        public Class<ReconcileScope.Habit> scopeType() {
            return ReconcileScope.Habit.class;
        }

        @Override
        public String sourceType() {
            return SOURCE_TYPE;
        }

        @Override
        public List<DesiredEntry> desiredEntries(ReconcileScope.Habit scope) {
            return log.tickedDatesFrom(scope.habitId(), scope.fromDate()).entrySet().stream()
                    .map(
                            e ->
                                    new DesiredEntry(
                                            e.getKey(),
                                            PointsCategory.HABIT,
                                            RULE_CODE,
                                            e.getValue(),
                                            SOURCE_TYPE,
                                            sourceIdFor(scope.habitId(), e.getKey()),
                                            "Test habit tick"))
                    .toList();
        }

        private UUID sourceIdFor(UUID habitId, LocalDate date) {
            // A stable id per (habit, date), the same way a real habit_log row's id
            // would be stable across reconciliation passes.
            return UUID.nameUUIDFromBytes((habitId + ":" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @TestConfiguration
    static class FakeHabitReconciliationConfig {
        @Bean
        FakeHabitLog fakeHabitLog() {
            return new FakeHabitLog();
        }

        @Bean
        ReconciliationCalculator<ReconcileScope.Habit> fakeHabitCalculator(FakeHabitLog log) {
            return new FakeHabitCalculator(log);
        }
    }
}
