package com.dailyforge.habit.repo;

import com.dailyforge.habit.domain.Habit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

    List<Habit> findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(UUID userId);

    List<Habit> findAllByUserIdOrderBySortOrderAsc(UUID userId);

    Optional<Habit> findByIdAndUserId(UUID id, UUID userId);

    /** Used to decide whether the commitment bonus is even askable ("at the first habit's creation"). */
    boolean existsByUserIdAndArchivedAtIsNull(UUID userId);
}
