package com.dailyforge.activity.api;

import com.dailyforge.activity.domain.ActivityLog;
import com.dailyforge.activity.domain.ActivityPolarity;
import com.dailyforge.activity.domain.ActivityType;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.PointsResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ActivityDtos {

    private ActivityDtos() {}

    public record CreateActivityTypeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull ActivityPolarity polarity,
            @NotNull @Min(1) Integer points,
            @NotBlank @Size(max = 40) String icon) {}

    public record ActivityTypeResponse(UUID id, String name, ActivityPolarity polarity, int points, String icon, int sortOrder) {
        public static ActivityTypeResponse of(ActivityType type) {
            return new ActivityTypeResponse(type.getId(), type.getName(), type.getPolarity(), type.getPoints(), type.getIcon(), type.getSortOrder());
        }
    }

    public record LogActivityRequest(@NotNull LocalDate date, @Min(1) Integer count, @Size(max = 500) String note) {}

    public record ActivityLogResponse(UUID id, UUID activityTypeId, LocalDate occurredOn, int count, String note, Instant createdAt) {
        public static ActivityLogResponse of(ActivityLog log) {
            return new ActivityLogResponse(log.getId(), log.getActivityTypeId(), log.getOccurredOn(), log.getCount(), log.getNote(), log.getCreatedAt());
        }
    }

    /** A log write returns the affected log plus the points envelope (spec §7). */
    public record LogWriteResponse(ActivityLogResponse log, PointsEnvelope points) {
        public static LogWriteResponse of(ActivityLog log, PointsResult result) {
            return new LogWriteResponse(ActivityLogResponse.of(log), PointsEnvelope.of(result));
        }
    }

    public record DeleteLogResponse(PointsEnvelope points) {
        public static DeleteLogResponse of(PointsResult result) {
            return new DeleteLogResponse(PointsEnvelope.of(result));
        }
    }
}
