package com.dailyforge.workout.repo;

import com.dailyforge.workout.domain.WorkoutSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {

    List<WorkoutSet> findAllBySessionIdAndDeletedAtIsNullOrderBySetNumberAsc(UUID sessionId);

    long countBySessionIdAndExerciseIdAndDeletedAtIsNull(UUID sessionId, UUID exerciseId);

    /**
     * Every set — deleted or not — ever logged by this user against this exercise. The
     * PR reconciler's scope needs deleted ones too: a set that used to hold the PR must
     * still be reachable so its stale award can be reversed (spec §5.3's own mechanism).
     *
     * A plain WHERE-clause join, not {@code join ... on}: {@link WorkoutSet} and
     * {@link com.dailyforge.workout.domain.WorkoutSession} have no mapped JPA
     * association between them (each entity only carries the other's id as a plain
     * column), so the two are joined here the same way any unrelated pair of entities
     * is in JPQL.
     */
    @Query(
            "select s from WorkoutSet s, WorkoutSession sess "
                    + "where sess.id = s.sessionId and sess.userId = :userId and s.exerciseId = :exerciseId")
    List<WorkoutSet> findAllForUserAndExercise(@Param("userId") UUID userId, @Param("exerciseId") UUID exerciseId);

    /** Ownership check for a single set, through its session — a set has no user_id column of its own. */
    @Query(
            "select s from WorkoutSet s, WorkoutSession sess "
                    + "where sess.id = s.sessionId and s.id = :id and sess.userId = :userId")
    Optional<WorkoutSet> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(
            "select s from WorkoutSet s, WorkoutSession sess "
                    + "where sess.id = s.sessionId and sess.userId = :userId and s.exerciseId = :exerciseId "
                    + "and s.deletedAt is null and sess.occurredOn >= :since "
                    + "order by s.totalWeightKg desc, s.reps desc, sess.occurredOn asc")
    List<WorkoutSet> findBestRecent(
            @Param("userId") UUID userId, @Param("exerciseId") UUID exerciseId, @Param("since") LocalDate since);

    @Query(
            "select s from WorkoutSet s, WorkoutSession sess "
                    + "where sess.id = s.sessionId and sess.userId = :userId and s.exerciseId = :exerciseId "
                    + "and s.deletedAt is null "
                    + "order by s.totalWeightKg desc, s.reps desc, sess.occurredOn asc")
    List<WorkoutSet> findBestLifetime(@Param("userId") UUID userId, @Param("exerciseId") UUID exerciseId);

    /**
     * Every non-deleted set ever logged for this exercise, newest first, alongside the
     * date it was logged on — the exercise history view (spec §11's own "history of
     * workout" request has no dedicated entity, so this reads the same rows the PR
     * calculators already do, just in chronological rather than best-first order).
     * {@code Object[]} rather than a projection interface: a plain WHERE-clause join
     * (no mapped association between {@link WorkoutSet} and
     * {@link com.dailyforge.workout.domain.WorkoutSession}) cannot bind into one.
     */
    @Query(
            "select s, sess.occurredOn from WorkoutSet s, WorkoutSession sess "
                    + "where sess.id = s.sessionId and sess.userId = :userId and s.exerciseId = :exerciseId "
                    + "and s.deletedAt is null "
                    + "order by sess.occurredOn desc, s.setNumber desc")
    List<Object[]> findHistory(@Param("userId") UUID userId, @Param("exerciseId") UUID exerciseId);
}
