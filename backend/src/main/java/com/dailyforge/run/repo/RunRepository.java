package com.dailyforge.run.repo;

import com.dailyforge.run.domain.Run;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, UUID> {

    Optional<Run> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** Chronological order — what the reconciler walks to decide "first ever" milestones. Live runs only. */
    List<Run> findAllByUserIdAndDeletedAtIsNullOrderByOccurredOnAscCreatedAtAsc(UUID userId);

    /**
     * Every run this user has ever logged, deleted or not — the reconciliation scope's
     * full "universe" of source ids, so a deleted run's stale ledger entries stay
     * reachable to reverse (the same reason {@code workout_set} is soft-deleted).
     */
    List<Run> findAllByUserId(UUID userId);

    List<Run> findAllByUserIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            UUID userId, LocalDate from, LocalDate to);

    List<Run> findAllByUserIdAndDeletedAtIsNullOrderByOccurredOnDescCreatedAtDesc(UUID userId);

    List<Run> findAllByUserIdAndDeletedAtIsNullOrderByDistanceMetersDescPaceSecPerKmAsc(UUID userId);

    List<Run> findAllByUserIdAndDeletedAtIsNullOrderByPaceSecPerKmAsc(UUID userId);

    List<Run> findAllByUserIdAndDeletedAtIsNullAndDistanceMetersGreaterThanEqualOrderByPaceSecPerKmAsc(
            UUID userId, int minDistanceMeters);
}
