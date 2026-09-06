package com.dailyforge.habit.repo;

import com.dailyforge.habit.domain.HabitLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitLogRepository extends JpaRepository<HabitLog, UUID> {

    Optional<HabitLog> findByHabitIdAndOccurredOn(UUID habitId, LocalDate occurredOn);

    /** Everything the reconciler and streak calculator read from, in date order. */
    List<HabitLog> findAllByHabitIdAndOccurredOnGreaterThanEqualOrderByOccurredOnAsc(
            UUID habitId, LocalDate fromDate);

    List<HabitLog> findAllByHabitIdIn(List<UUID> habitIds);

    boolean existsByHabitId(UUID habitId);

    /** Every log for a set of habits on one date — the raw data the commitment check reads. */
    List<HabitLog> findAllByHabitIdInAndOccurredOn(List<UUID> habitIds, LocalDate occurredOn);
}
