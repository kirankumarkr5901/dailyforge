package com.dailyforge.activity.domain;

import com.dailyforge.activity.repo.ActivityLogRepository;
import com.dailyforge.activity.repo.ActivityTypeRepository;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Positive and negative one-off activities (spec §6 "activity", §8's own drawer entry)
 * — the loop's third way of earning or losing points alongside the structured
 * trackers, for whatever a habit, workout, run, or job entry does not already cover.
 * Each log is a direct source-tracked award, exactly like a reward redemption: no
 * reconciliation engine, since there is no history to recompute against — deleting a
 * log simply reverses the one award it made.
 */
@Service
public class ActivityService {

    private static final String SOURCE_TYPE = "ACTIVITY_LOG";

    private final ActivityTypeRepository types;
    private final ActivityLogRepository logs;
    private final PointsService points;
    private final DayService dayService;
    private final IdentityService identity;

    public ActivityService(
            ActivityTypeRepository types,
            ActivityLogRepository logs,
            PointsService points,
            DayService dayService,
            IdentityService identity) {
        this.types = types;
        this.logs = logs;
        this.points = points;
        this.dayService = dayService;
        this.identity = identity;
    }

    @Transactional
    public ActivityType create(UUID userId, String name, ActivityPolarity polarity, int points, String icon) {
        int nextSort =
                types.findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(userId).stream()
                        .mapToInt(ActivityType::getSortOrder)
                        .max()
                        .orElse(-1)
                        + 1;
        return types.save(ActivityType.create(userId, name, polarity, points, icon, nextSort));
    }

    @Transactional(readOnly = true)
    public List<ActivityType> list(UUID userId) {
        return types.findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(userId);
    }

    @Transactional
    public void archive(UUID id, UUID userId) {
        ActivityType type = requireOwnedType(id, userId);
        type.archive(java.time.Instant.now());
        types.save(type);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> recentLogs(UUID userId) {
        return logs.findAllByUserIdOrderByOccurredOnDescCreatedAtDesc(userId);
    }

    public record LoggedActivity(ActivityLog log, PointsResult points) {}

    @Transactional
    public LoggedActivity log(UUID activityTypeId, UUID userId, LocalDate date, int count, String note) {
        ActivityType type = requireOwnedType(activityTypeId, userId);
        if (type.isArchived()) {
            throw ApiException.notFound("That activity");
        }
        if (count < 1) {
            throw ApiException.outOfRange("count", "Log at least one.");
        }

        ActivityLog log = logs.save(ActivityLog.create(activityTypeId, userId, date, count, note));

        int amount = type.signedPoints() * count;
        PointsResult result =
                points.award(
                        new AwardCommand(
                                userId,
                                date,
                                PointsCategory.ACTIVITY,
                                type.getPolarity() == ActivityPolarity.POSITIVE ? "ACTIVITY_POSITIVE" : "ACTIVITY_NEGATIVE",
                                amount,
                                SOURCE_TYPE,
                                log.getId(),
                                type.getName() + (count > 1 ? " ×" + count : ""),
                                "activity-log:" + log.getId()));

        return new LoggedActivity(log, result);
    }

    @Transactional
    public PointsResult deleteLog(UUID logId, UUID userId) {
        ActivityLog log = logs.findByIdAndUserId(logId, userId).orElseThrow(() -> ApiException.notFound("That log"));
        int reversedAmount = log.getCount() * requireOwnedType(log.getActivityTypeId(), userId).signedPoints();
        points.reverseBySource(SOURCE_TYPE, log.getId(), "Activity log removed");
        logs.delete(log);

        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        int newTotal = points.snapshot(userId, zone).total();
        return new PointsResult(List.of(), -reversedAmount, newTotal, List.of());
    }

    private ActivityType requireOwnedType(UUID id, UUID userId) {
        return types.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That activity"));
    }
}
