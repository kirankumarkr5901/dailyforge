package com.dailyforge.points.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.points.repo.UserScoreCacheRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The engine. Every method here runs inside a single transaction that covers both the
 * ledger write and the cache update — the two must never be visible out of step with
 * each other (spec §5.1: "updated in the same transaction").
 */
@Service
public class PointsServiceImpl implements PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsServiceImpl.class);

    private final PointsEntryRepository entries;
    private final UserScoreCacheRepository caches;
    private final DayService dayService;
    private final PointsRuleConfigService ruleConfigs;
    private final List<ReconciliationCalculator<?>> calculators;

    public PointsServiceImpl(
            PointsEntryRepository entries,
            UserScoreCacheRepository caches,
            DayService dayService,
            PointsRuleConfigService ruleConfigs,
            List<ReconciliationCalculator<?>> calculators) {
        this.entries = entries;
        this.caches = caches;
        this.dayService = dayService;
        this.ruleConfigs = ruleConfigs;
        this.calculators = calculators;
    }

    @Override
    @Transactional
    public PointsResult award(AwardCommand cmd) {
        Optional<PointsEntry> existing = entries.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), cmd);
        }

        checkDailyCap(cmd);

        PointsEntry entry =
                PointsEntry.create(
                        cmd.userId(),
                        cmd.occurredOn(),
                        cmd.category(),
                        cmd.ruleCode(),
                        cmd.amount(),
                        cmd.sourceType(),
                        cmd.sourceId(),
                        null,
                        cmd.description(),
                        cmd.idempotencyKey());

        try {
            entries.saveAndFlush(entry);
        } catch (DataIntegrityViolationException raced) {
            // Two requests carrying the same key arrived together; the loser here is the
            // winner of an idempotent retry, not a failure.
            PointsEntry theWinner =
                    entries.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey())
                            .orElseThrow(() -> raced);
            return replayOrConflict(theWinner, cmd);
        }

        int newTotal = applyToCache(cmd.userId(), cmd.amount());
        log.info(
                "Awarded {} {} to user {} (rule={}, source={}/{})",
                cmd.amount(),
                cmd.category(),
                cmd.userId(),
                cmd.ruleCode(),
                cmd.sourceType(),
                cmd.sourceId());

        return new PointsResult(
                List.of(entry), cmd.amount(), newTotal, celebrationsFor(cmd.ruleCode(), cmd.amount()));
    }

    /** An idempotency key already used. Either a safe replay, or a genuine conflict. */
    private PointsResult replayOrConflict(PointsEntry existing, AwardCommand cmd) {
        boolean matches =
                existing.getUserId().equals(cmd.userId())
                        && existing.getCategory() == cmd.category()
                        && existing.getRuleCode().equals(cmd.ruleCode())
                        && existing.getAmount() == cmd.amount()
                        && existing.getOccurredOn().equals(cmd.occurredOn())
                        && Objects.equals(existing.getSourceType(), cmd.sourceType())
                        && Objects.equals(existing.getSourceId(), cmd.sourceId());

        if (!matches) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    HttpStatus.CONFLICT,
                    "That action was already recorded differently. Refresh and try again.");
        }

        int currentTotal = caches.findById(cmd.userId()).map(UserScoreCache::getTotalPoints).orElse(0);
        return new PointsResult(
                List.of(existing), existing.getAmount(), currentTotal, celebrationsFor(existing.getRuleCode(), existing.getAmount()));
    }

    @Override
    @Transactional
    public void reverseBySource(String sourceType, UUID sourceId, String reason) {
        List<PointsEntry> toReverse = entries.findAllBySourceTypeAndSourceIdAndReversedFalseAndReversesIdIsNull(sourceType, sourceId);
        for (PointsEntry original : toReverse) {
            reverseOne(original, reason);
        }
    }

    private void reverseOne(PointsEntry original, String reason) {
        String key = IdempotencyKeys.forReversal(original.getId());

        // Idempotent on its own terms: this key can only ever exist as the reversal of
        // exactly this entry, so finding it already present means the work is done.
        if (entries.findByUserIdAndIdempotencyKey(original.getUserId(), key).isPresent()) {
            return;
        }

        PointsEntry reversal =
                PointsEntry.create(
                        original.getUserId(),
                        original.getOccurredOn(),
                        original.getCategory(),
                        original.getRuleCode(),
                        -original.getAmount(),
                        original.getSourceType(),
                        original.getSourceId(),
                        original.getId(),
                        reason,
                        key);

        try {
            entries.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException raced) {
            return; // Someone else's concurrent call already wrote this exact reversal.
        }

        applyToCache(original.getUserId(), -original.getAmount());
        original.markReversed();
        entries.save(original);

        log.info(
                "Reversed {} {} for user {} ({}: {})",
                original.getAmount(),
                original.getCategory(),
                original.getUserId(),
                original.getRuleCode(),
                reason);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void reconcile(UUID userId, ReconcileScope scope) {
        ReconciliationCalculator<ReconcileScope> calculator =
                calculators.stream()
                        .filter(c -> c.scopeType().isInstance(scope))
                        .map(c -> (ReconciliationCalculator<ReconcileScope>) c)
                        .findFirst()
                        .orElse(null);

        if (calculator == null) {
            // No module owns this scope yet (habits arrive at M3, workouts at M4, …).
            // A scope with nothing registered against it is not an error — it is every
            // scope, until its owning module exists.
            log.debug("No reconciliation calculator registered for {}", scope.getClass().getSimpleName());
            return;
        }

        String sourceType = calculator.sourceType();
        List<DesiredEntry> desired = calculator.desiredEntries(scope);
        List<PointsEntry> existing = entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(userId, sourceType);

        Map<ReconcileKey, DesiredEntry> desiredByKey = new java.util.HashMap<>();
        for (DesiredEntry d : desired) {
            desiredByKey.put(new ReconcileKey(d.sourceId(), d.ruleCode()), d);
        }

        Map<ReconcileKey, PointsEntry> existingByKey = new java.util.HashMap<>();
        for (PointsEntry e : existing) {
            existingByKey.put(new ReconcileKey(e.getSourceId(), e.getRuleCode()), e);
        }

        // Exists but should not (or should, at a different amount): reverse it.
        for (Map.Entry<ReconcileKey, PointsEntry> entry : existingByKey.entrySet()) {
            DesiredEntry stillWanted = desiredByKey.get(entry.getKey());
            if (stillWanted == null || stillWanted.amount() != entry.getValue().getAmount()) {
                reverseOne(entry.getValue(), "Reconciliation: recomputed from current data");
            }
        }

        // Should exist but doesn't (or existed at a different amount, just reversed above).
        for (Map.Entry<ReconcileKey, DesiredEntry> entry : desiredByKey.entrySet()) {
            PointsEntry already = existingByKey.get(entry.getKey());
            DesiredEntry d = entry.getValue();
            if (already == null || already.getAmount() != d.amount()) {
                // The generation number is what lets this same (source, rule) slot be
                // awarded again after an earlier occasion was reversed, without colliding
                // with that earlier occasion's permanent ledger row.
                long generation = entries.countBySourceTypeAndSourceIdAndRuleCode(d.sourceType(), d.sourceId(), d.ruleCode());
                award(
                        new AwardCommand(
                                userId,
                                d.occurredOn(),
                                d.category(),
                                d.ruleCode(),
                                d.amount(),
                                d.sourceType(),
                                d.sourceId(),
                                d.description(),
                                IdempotencyKeys.forReconciledAward(d.sourceType(), d.sourceId(), d.ruleCode(), generation)));
            }
        }
    }

    private record ReconcileKey(UUID sourceId, String ruleCode) {}

    /**
     * Spec §5.7: a per-category ceiling on how many points a single day can earn, so a
     * gamed input (200 sets logged for free points) has a bound. Reversals never reach
     * this check — they persist their entry directly rather than through {@code award()}
     * — and neither does any award that reduces or zeroes a total, since the guardrail
     * exists to stop earning past a limit, not to obstruct correcting one.
     */
    private void checkDailyCap(AwardCommand cmd) {
        if (cmd.amount() <= 0) {
            return;
        }

        RuleConfig dailyCap = ruleConfigs.getSystemDefault("DAILY_CAP");
        if (!dailyCap.enabled()) {
            return;
        }

        Integer cap = dailyCap.getIntOrNull(cmd.category().name());
        if (cap == null) {
            return; // this category is uncapped
        }

        int earnedToday = entries.sumAmountByCategoryOnDate(cmd.userId(), cmd.category(), cmd.occurredOn());
        if (earnedToday + cmd.amount() > cap) {
            throw new ApiException(
                    ErrorCode.DAILY_CAP_REACHED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "You've reached today's cap for this category. It resets tomorrow.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ScoreSnapshot snapshot(UUID userId, ZoneId zone) {
        LocalDate today = dayService.today(zone);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        int total = caches.findById(userId).map(UserScoreCache::getTotalPoints).orElse(0);
        int todayTotal = entries.sumAmountBetween(userId, today, today);
        int weekTotal = entries.sumAmountBetween(userId, weekStart, today);
        int monthTotal = entries.sumAmountBetween(userId, monthStart, today);

        Map<PointsCategory, Integer> byCategory = new EnumMap<>(PointsCategory.class);
        for (PointsCategory category : PointsCategory.values()) {
            byCategory.put(category, entries.sumAmountByCategoryBetween(userId, category, monthStart, today));
        }

        return new ScoreSnapshot(total, todayTotal, weekTotal, monthTotal, byCategory);
    }

    @Override
    @Transactional
    public void recalculate(UUID userId) {
        UserScoreCache cache = caches.lockForUpdate(userId).orElseGet(() -> UserScoreCache.empty(userId));
        cache.rebuild(entries.sumAmountForUser(userId), entries.countForUser(userId));
        caches.save(cache);
    }

    /** Locks the cache row (creating it on first use) and applies one signed delta. */
    private int applyToCache(UUID userId, int delta) {
        UserScoreCache cache = lockOrCreateCache(userId);
        cache.apply(delta);
        caches.save(cache);
        return cache.getTotalPoints();
    }

    private UserScoreCache lockOrCreateCache(UUID userId) {
        return caches.lockForUpdate(userId)
                .orElseGet(
                        () -> {
                            try {
                                UserScoreCache created = UserScoreCache.empty(userId);
                                caches.saveAndFlush(created);
                                return created;
                            } catch (DataIntegrityViolationException raced) {
                                // Another concurrent first-award for this user just created it.
                                return caches.lockForUpdate(userId)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalStateException(
                                                                "Score cache vanished for " + userId));
                            }
                        });
    }

    private List<Celebration> celebrationsFor(String ruleCode, int amount) {
        // A negative amount is a penalty or a reversal, and neither should replay a
        // celebration. Zero is deliberately allowed through: WORKOUT_SESSION_COMPLETE
        // is worth zero points and its whole purpose is the celebration (spec §5.4).
        if (amount < 0) {
            return List.of();
        }
        List<Celebration> celebrations = new ArrayList<>();
        Celebration.typeFor(ruleCode).ifPresent(type -> celebrations.add(Celebration.of(type)));
        return celebrations;
    }
}
