package com.dailyforge.habit.domain;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stable source ids for ledger entries the habit module writes.
 *
 * Derived from (habit, date) rather than from a {@code habit_log} row's own id, because
 * a day that was never logged at all (missed, with no row) still needs a stable identity
 * for its penalty entry, and one derived this way exists whether or not a log row does.
 * The same id is reused across every rule code for that day (base, streak, penalty) —
 * one raw day justifies several distinct ledger lines, matched by rule code, per the
 * reconciliation engine's own natural key (spec §5.3).
 */
final class HabitSourceIds {

    private HabitSourceIds() {}

    static UUID forHabitDay(UUID habitId, LocalDate date) {
        return UUID.nameUUIDFromBytes((habitId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }

    static UUID forDayCommitment(UUID userId, LocalDate date) {
        return UUID.nameUUIDFromBytes(("commitment:" + userId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }
}
