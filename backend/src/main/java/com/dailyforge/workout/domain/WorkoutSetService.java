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

    public WorkoutSetService(
            WorkoutSessionRepository sessions,
            WorkoutSetRepository sets,
            PlanExerciseRepository planExercises,
            ExerciseService exerciseService,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.sessions = sessions;
        this.sets = sets;
        this.planExercises = planExercises;
        this.exerciseService = exerciseService;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
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
        PointsResult completion = checkSessionComplete(session);
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

    /** Recent (last 90 days) and lifetime best set for an exercise — display only (spec §5.5). */
    @Transactional(readOnly = true)
    public PrView recentPr(UUID userId, UUID exerciseId, LocalDate today) {
        return sets.findBestRecent(userId, exerciseId, today.minusDays(90)).stream()
                .findFirst()
                .map(s -> toView(s))
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

    /**
     * Fires the zero-point, celebration-only completion award (spec §5.4) the moment
     * every exercise scheduled for this session's plan day has at least one live set.
     * Only meaningful for a session opened against a real plan day — a freeform session
     * has no fixed exercise list to be "complete" against.
     */
    private PointsResult checkSessionComplete(WorkoutSession session) {
        if (session.getPlanId() == null || session.getDayIndex() == null) {
            return zeroResult(session.getUserId());
        }
        var config = ruleConfigs.getSystemDefault("WORKOUT_SESSION_COMPLETE");
        if (!config.enabled()) {
            return zeroResult(session.getUserId());
        }

        Set<UUID> required =
                planExercises.findAllByPlanIdAndDayIndexOrderBySortOrderAsc(session.getPlanId(), session.getDayIndex()).stream()
                        .map(PlanExercise::getExerciseId)
                        .collect(Collectors.toSet());
        if (required.isEmpty()) {
            return zeroResult(session.getUserId());
        }

        List<WorkoutSet> logged = sets.findAllBySessionIdAndDeletedAtIsNullOrderBySetNumberAsc(session.getId());
        Set<UUID> loggedExercises = logged.stream().map(WorkoutSet::getExerciseId).collect(Collectors.toSet());
        if (!loggedExercises.containsAll(required)) {
            return zeroResult(session.getUserId());
        }

        int amount = config.getInt("points");
        return points.award(
                AwardCommand.withoutSource(
                        session.getUserId(),
                        session.getOccurredOn(),
                        PointsCategory.WORKOUT,
                        "WORKOUT_SESSION_COMPLETE",
                        amount,
                        "Session complete",
                        "workout-session-complete:" + session.getId()));
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
