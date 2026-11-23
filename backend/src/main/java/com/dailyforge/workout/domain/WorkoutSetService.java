package com.dailyforge.workout.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.workout.repo.PlanExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSessionRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging itself: opening a session, recording a set, and reversing that on delete.
 * Every mutation ends the same way — reconciling {@link ReconcileScope.ExercisePr} for
 * the affected exercise, since a single set changing can both earn its own point and
 * shift who holds the exercise's personal record (spec §5.3).
 */
@Service
public class WorkoutSetService {

    private final WorkoutSessionRepository sessions;
    private final WorkoutSetRepository sets;
    private final PlanExerciseRepository planExercises;
    private final ExerciseService exerciseService;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;
    private final WorkoutCompletionService completions;
    private final DeloadService deload;

    public WorkoutSetService(
            WorkoutSessionRepository sessions,
            WorkoutSetRepository sets,
            PlanExerciseRepository planExercises,
            ExerciseService exerciseService,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity,
            WorkoutCompletionService completions,
            DeloadService deload) {
        this.sessions = sessions;
        this.sets = sets;
        this.planExercises = planExercises;
        this.exerciseService = exerciseService;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
        this.completions = completions;
        this.deload = deload;
    }

    @Transactional
    public WorkoutSession getOrCreateSession(UUID userId, LocalDate date, UUID planId, Integer dayIndex) {
        requireEditableDate(userId, date);
        return sessions
                .findByUserIdAndOccurredOnAndPlanIdAndDayIndex(userId, date, planId, dayIndex)
                .orElseGet(() -> sessions.save(WorkoutSession.start(userId, date, planId, dayIndex)));
    }

    @Transactional(readOnly = true)
    public List<WorkoutSet> setsFor(UUID sessionId) {
        return sets.findAllBySessionIdAndDeletedAtIsNullOrderBySetNumberAsc(sessionId);
    }

    /** One entry per set, newest first — the exercise history view. */
    public record HistoryEntry(LocalDate date, WorkoutSet set) {}

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID userId, UUID exerciseId) {
        return sets.findHistory(userId, exerciseId).stream()
                .map(row -> new HistoryEntry((LocalDate) row[1], (WorkoutSet) row[0]))
                .toList();
    }

    /** A set write returns the set itself alongside the points envelope (spec §7). */
    public record SetWrite(WorkoutSet set, PointsResult points) {}

    @Transactional
    public SetWrite logSet(
            UUID userId,
            LocalDate date,
            UUID exerciseId,
            UUID planId,
            Integer dayIndex,
            BigDecimal enteredWeight,
            WeightMode weightMode,
            BigDecimal addedWeight,
            int reps) {
        requireEditableDate(userId, date);
        Exercise exercise = exerciseService.requireVisible(exerciseId, userId);
        checkSanityLimits(exercise.getEquipment(), enteredWeight, addedWeight, reps);

        WorkoutSession session = getOrCreateSession(userId, date, planId, dayIndex);
        int setNumber = (int) sets.countBySessionIdAndExerciseIdAndDeletedAtIsNull(session.getId(), exerciseId) + 1;

        WorkoutSet set =
                WorkoutSet.log(session.getId(), exerciseId, exercise.getEquipment(), setNumber, enteredWeight, weightMode, addedWeight, reps);
        sets.save(set);

        PointsResult reconciled = points.reconcile(userId, new ReconcileScope.ExercisePr(userId, exerciseId));
        PointsResult completion = completeOnTarget(session, exerciseId, setNumber, userId);
        return new SetWrite(set, PointsResult.combine(List.of(reconciled, completion)));
    }

    @Transactional
    public SetWrite updateSet(
            UUID userId, UUID setId, BigDecimal enteredWeight, WeightMode weightMode, BigDecimal addedWeight, int reps) {
        WorkoutSet set = requireOwned(setId, userId);
        WorkoutSession session = requireSession(set.getSessionId());
        requireEditableDate(userId, session.getOccurredOn());
        Exercise exercise = exerciseService.requireVisible(set.getExerciseId(), userId);
        checkSanityLimits(exercise.getEquipment(), enteredWeight, addedWeight, reps);

        set.apply(exercise.getEquipment(), enteredWeight, weightMode, addedWeight);
        set.updateReps(reps);
        sets.save(set);

        PointsResult result = points.reconcile(userId, new ReconcileScope.ExercisePr(userId, set.getExerciseId()));
        return new SetWrite(set, result);
    }

    @Transactional
    public PointsResult deleteSet(UUID userId, UUID setId) {
        WorkoutSet set = requireOwned(setId, userId);
        WorkoutSession session = requireSession(set.getSessionId());
        requireEditableDate(userId, session.getOccurredOn());

        set.softDelete(Instant.now());
        sets.save(set);

        return points.reconcile(userId, new ReconcileScope.ExercisePr(userId, set.getExerciseId()));
    }

    /**
     * The set that reaches the plan's target finishes the exercise. Only the set that
     * lands exactly on the target does it: once you reopen an exercise to do an extra
     * set, logging that set must not close it again behind your back.
     */
    private PointsResult completeOnTarget(WorkoutSession session, UUID exerciseId, int setNumber, UUID userId) {
        if (session.getPlanId() == null || session.getDayIndex() == null) {
            return zeroResult(userId);
        }
        boolean deloadWeek = deload.isDeload(userId, session.getOccurredOn());
        boolean reachedTarget =
                planExercises.findAllByPlanIdAndDayIndexOrderBySortOrderAsc(session.getPlanId(), session.getDayIndex()).stream()
                        .filter(pe -> pe.getExerciseId().equals(exerciseId) && pe.getTargetSets() != null)
                        .map(pe -> deloadWeek ? DeloadService.deloadSets(pe.getTargetSets()) : pe.getTargetSets())
                        .anyMatch(target -> target == setNumber);
        // Finishing this exercise can finish the session, so its result carries that celebration.
        return reachedTarget ? completions.markComplete(session.getId(), exerciseId, userId) : zeroResult(userId);
    }

    /** The add-weight hint for an exercise on {@code date}, judged on the last day it was done before that. */
    @Transactional(readOnly = true)
    public java.util.Optional<ProgressionCalculator.Suggestion> progression(
            UUID userId, Exercise exercise, Integer targetSets, Integer targetReps, LocalDate date) {
        if (targetReps == null) {
            return java.util.Optional.empty();
        }
        return sets.findLastDateBefore(userId, exercise.getId(), date)
                .flatMap(last -> ProgressionCalculator.suggest(
                        exercise.getEquipment(), targetSets, targetReps, last, sets.findOnDate(userId, exercise.getId(), last)));
    }

    /**
     * "Recent PR": the best set from the last day this exercise was done before
     * {@code date}. Doing chest press on Monday and again on Thursday, Thursday's card
     * shows Monday's best, so today's sets have something concrete to beat.
     */
    @Transactional(readOnly = true)
    public PrView lastSessionBest(UUID userId, UUID exerciseId, LocalDate date) {
        return sets.findLastDateBefore(userId, exerciseId, date)
                .flatMap(last -> sets.findOnDate(userId, exerciseId, last).stream()
                        .max(java.util.Comparator.comparing(WorkoutSet::getTotalWeightKg)
                                .thenComparingInt(WorkoutSet::getReps))
                        .map(best -> new PrView(best.getTotalWeightKg(), best.getReps(), last)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PrView lifetimePr(UUID userId, UUID exerciseId) {
        return sets.findBestLifetime(userId, exerciseId).stream().findFirst().map(this::toView).orElse(null);
    }

    private PrView toView(WorkoutSet set) {
        LocalDate occurredOn = requireSession(set.getSessionId()).getOccurredOn();
        return new PrView(set.getTotalWeightKg(), set.getReps(), occurredOn);
    }

    private PointsResult zeroResult(UUID userId) {
        return new PointsResult(List.of(), 0, points.snapshot(userId, ZoneId.of("UTC")).total(), List.of());
    }

    private void checkSanityLimits(Equipment equipment, BigDecimal enteredWeight, BigDecimal addedWeight, int reps) {
        var limits = ruleConfigs.getSystemDefault("SANITY_LIMITS");
        int maxWeight = limits.getInt("setMaxWeightKg");
        int maxReps = limits.getInt("setMaxReps");

        // A bodyweight exercise's enteredWeight never enters totalWeightKg (spec §5.5) —
        // only addedWeight does — so only addedWeight is worth bounding for one.
        BigDecimal weight =
                equipment == Equipment.BODYWEIGHT
                        ? BigDecimal.ZERO
                        : (enteredWeight == null ? BigDecimal.ZERO : enteredWeight);
        BigDecimal added = addedWeight == null ? BigDecimal.ZERO : addedWeight;
        if (weight.compareTo(BigDecimal.valueOf(maxWeight)) > 0 || added.compareTo(BigDecimal.valueOf(maxWeight)) > 0) {
            throw ApiException.outOfRange("weight", "That looks out of range. Check the weight.");
        }
        if (reps < 0 || reps > maxReps) {
            throw ApiException.outOfRange("reps", "That looks out of range. Check the reps.");
        }
    }

    private void requireEditableDate(UUID userId, LocalDate date) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        var window = ruleConfigs.getSystemDefault("EDIT_WINDOW");
        int generalDays = window.getInt("generalDays");
        if (!dayService.isWithinEditWindow(date, zone, generalDays)) {
            throw new ApiException(ErrorCode.ENTRY_LOCKED, HttpStatus.FORBIDDEN, "This day can no longer be changed.");
        }
    }

    /** Public so the controller can read the current version for the stale-write check. */
    public WorkoutSet requireOwned(UUID setId, UUID userId) {
        return sets.findByIdAndUserId(setId, userId).orElseThrow(() -> ApiException.notFound("That set"));
    }

    private WorkoutSession requireSession(UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("That session"));
    }

    public record PrView(BigDecimal totalWeightKg, int reps, LocalDate achievedOn) {}
}
