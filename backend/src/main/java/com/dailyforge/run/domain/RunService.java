package com.dailyforge.run.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.run.repo.RunRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging a run and reconciling the whole-history PR/milestone scope afterwards (spec
 * §5.3) — the same shape M4's {@code WorkoutSetService} uses for a set: mutate the raw
 * source row, then let {@link ReconcileScope.RunPr} recompute what the ledger should say.
 */
@Service
public class RunService {

    private final RunRepository runs;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public RunService(
            RunRepository runs,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.runs = runs;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    public record RunWrite(Run run, PointsResult points) {}

    @Transactional
    public RunWrite log(UUID userId, LocalDate date, int distanceMeters, int durationSeconds, RunType requestedType, String note, Integer feltEffort) {
        requireEditableDate(userId, date);
        requireValidType(distanceMeters, requestedType);
        checkSanityLimits(distanceMeters, durationSeconds);

        Run run = Run.log(userId, date, distanceMeters, durationSeconds, requestedType, note, feltEffort);
        runs.save(run);

        PointsResult result = points.reconcile(userId, new ReconcileScope.RunPr(userId));
        return new RunWrite(run, result);
    }

    @Transactional
    public RunWrite update(
            UUID userId, UUID runId, int distanceMeters, int durationSeconds, RunType requestedType, String note, Integer feltEffort) {
        Run run = requireOwned(runId, userId);
        requireEditableDate(userId, run.getOccurredOn());
        requireValidType(distanceMeters, requestedType);
        checkSanityLimits(distanceMeters, durationSeconds);

        run.apply(distanceMeters, durationSeconds, requestedType, note, feltEffort);
        runs.save(run);

        PointsResult result = points.reconcile(userId, new ReconcileScope.RunPr(userId));
        return new RunWrite(run, result);
    }

    @Transactional
    public PointsResult delete(UUID userId, UUID runId) {
        Run run = requireOwned(runId, userId);
        requireEditableDate(userId, run.getOccurredOn());
        run.softDelete(java.time.Instant.now());
        runs.save(run);
        return points.reconcile(userId, new ReconcileScope.RunPr(userId));
    }

    @Transactional(readOnly = true)
    public List<Run> list(UUID userId, LocalDate from, LocalDate to) {
        return from != null && to != null
                ? runs.findAllByUserIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(userId, from, to)
                : runs.findAllByUserIdAndDeletedAtIsNullOrderByOccurredOnDescCreatedAtDesc(userId);
    }

    public Run requireOwned(UUID id, UUID userId) {
        return runs.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> ApiException.notFound("That run"));
    }

    /** A run-distance goal's progress (spec §8.6: "60 km this month") — the sum over a period. */
    @Transactional(readOnly = true)
    public int totalDistanceMeters(UUID userId, LocalDate from, LocalDate to) {
        return list(userId, from, to).stream().mapToInt(Run::getDistanceMeters).sum();
    }

    private void requireValidType(int distanceMeters, RunType requestedType) {
        if (!Run.isLongDistance(distanceMeters) && requestedType == RunType.LONG) {
            throw ApiException.outOfRange("type", "Long is reserved for runs of 10 km or more.");
        }
        if (!Run.isLongDistance(distanceMeters) && requestedType == null) {
            throw ApiException.outOfRange("type", "Choose interval or tempo for a run under 10 km.");
        }
    }

    private void checkSanityLimits(int distanceMeters, int durationSeconds) {
        var limits = ruleConfigs.getSystemDefault("SANITY_LIMITS");
        int maxMetres = limits.getInt("runMaxMetres");
        int maxSeconds = limits.getInt("runMaxSeconds");
        if (distanceMeters <= 0 || distanceMeters > maxMetres) {
            throw ApiException.outOfRange("distanceMeters", "That looks out of range. Check the distance.");
        }
        if (durationSeconds <= 0 || durationSeconds > maxSeconds) {
            throw ApiException.outOfRange("durationSeconds", "That looks out of range. Check the duration.");
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
}
