package com.dailyforge.points.repo;

import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method here is scoped by {@code userId} (spec §10, non-negotiable #8) — there is
 * no finder in this interface that could accidentally return another user's rows.
 */
public interface PointsEntryRepository extends JpaRepository<PointsEntry, UUID> {

    Optional<PointsEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * All *active* entries carrying a given source, for a specific user — "active"
     * meaning not reversed, AND not itself a reversal. A reversal row shares its
     * original's source_type/source_id (spec §5.1) but is never itself reversed, so
     * without excluding {@code reversesId}, a reversal would appear as an "active"
     * entry for its own source forever, and something that reverses "active entries for
     * a source" would eventually try to reverse a reversal.
     */
    List<PointsEntry> findAllByUserIdAndSourceTypeAndSourceIdAndReversedFalseAndReversesIdIsNull(
            UUID userId, String sourceType, UUID sourceId);

    /**
     * Same, without a userId — {@code reverseBySource} (spec §5.2's engine API) is not
     * given one, because the source id is enough to find the owner. Source ids are
     * globally unique UUIDs, so this cannot cross into another user's rows by accident.
     */
    List<PointsEntry> findAllBySourceTypeAndSourceIdAndReversedFalseAndReversesIdIsNull(
            String sourceType, UUID sourceId);

    /**
     * Every active (non-reversed, non-reversal) entry from a source type this user has,
     * regardless of which specific source id — the reconciliation engine needs the full
     * set to tell "should exist but doesn't" apart from "exists but shouldn't any more"
     * (spec §5.3). Reversal rows are excluded for the same reason as above: they are
     * bookkeeping about a past entry, not a fact the diff should treat as a live award.
     */
    List<PointsEntry> findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(
            UUID userId, String sourceType);

    /**
     * The same, narrowed to a specific set of source ids — what reconciliation actually
     * uses. Two different habits share the source type "HABIT_LOG"; without this
     * narrowing, reconciling one would see the other's entries as no longer desired and
     * reverse them.
     */
    List<PointsEntry> findAllByUserIdAndSourceTypeAndSourceIdInAndReversedFalseAndReversesIdIsNull(
            UUID userId, String sourceType, java.util.Collection<UUID> sourceIds);

    /**
     * Every entry — reversed or not, reversal or not — that has ever existed for this
     * exact (source, rule). Reconciliation uses this as a generation counter: the same
     * (source, rule) slot can be awarded, reversed, and awarded again arbitrarily many
     * times over a ledger's life (a habit ticked, unticked, and ticked again), and each
     * such award needs its own idempotency key rather than colliding with the first.
     */
    long countBySourceTypeAndSourceIdAndRuleCode(String sourceType, UUID sourceId, String ruleCode);

    Page<PointsEntry> findAllByUserIdOrderByOccurredOnDescCreatedAtDesc(UUID userId, Pageable pageable);

    Page<PointsEntry> findAllByUserIdAndCategoryOrderByOccurredOnDescCreatedAtDesc(
            UUID userId, PointsCategory category, Pageable pageable);

    Page<PointsEntry> findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    Page<PointsEntry> findAllByUserIdAndCategoryAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            UUID userId, PointsCategory category, LocalDate from, LocalDate to, Pageable pageable);

    @Query(
            "select coalesce(sum(e.amount), 0) from PointsEntry e "
                    + "where e.userId = :userId and e.occurredOn between :from and :to")
    int sumAmountBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(
            "select coalesce(sum(e.amount), 0) from PointsEntry e "
                    + "where e.userId = :userId and e.category = :category "
                    + "and e.occurredOn between :from and :to")
    int sumAmountByCategoryBetween(
            @Param("userId") UUID userId,
            @Param("category") PointsCategory category,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from PointsEntry e where e.userId = :userId")
    int sumAmountForUser(@Param("userId") UUID userId);

    @Query("select count(e) from PointsEntry e where e.userId = :userId")
    int countForUser(@Param("userId") UUID userId);

    /**
     * The daily cap guardrail (spec §5.7) reads today's total for one category before
     * deciding whether another award is allowed.
     */
    default int sumAmountByCategoryOnDate(UUID userId, PointsCategory category, LocalDate date) {
        return sumAmountByCategoryBetween(userId, category, date, date);
    }
}
