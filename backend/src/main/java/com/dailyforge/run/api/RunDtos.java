package com.dailyforge.run.api;

import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.run.domain.Run;
import com.dailyforge.run.domain.RunRecordsService.Bracket;
import com.dailyforge.run.domain.RunType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RunDtos {

    private RunDtos() {}

    public record LogRunRequest(
            @NotNull LocalDate date,
            @NotNull Integer distanceMeters,
            @NotNull Integer durationSeconds,
            RunType type,
            @Size(max = 280) String note,
            @Min(1) @Max(5) Integer feltEffort) {}

    public record RunResponse(
            UUID id,
            LocalDate date,
            int distanceMeters,
            int durationSeconds,
            RunType type,
            int paceSecPerKm,
            String note,
            Integer feltEffort,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static RunResponse of(Run run) {
            return new RunResponse(
                    run.getId(),
                    run.getOccurredOn(),
                    run.getDistanceMeters(),
                    run.getDurationSeconds(),
                    run.getType(),
                    run.getPaceSecPerKm(),
                    run.getNote(),
                    run.getFeltEffort(),
                    run.getVersion());
        }
    }

    public record RunWriteResponse(RunResponse run, PointsEnvelope points) {
        public static RunWriteResponse of(Run run, PointsResult result) {
            return new RunWriteResponse(RunResponse.of(run), PointsEnvelope.of(result));
        }
    }

    public record DeleteRunResponse(PointsEnvelope points) {
        public static DeleteRunResponse of(PointsResult result) {
            return new DeleteRunResponse(PointsEnvelope.of(result));
        }
    }

    public record RecordsResponse(
            int lifetimeRunPoints,
            List<RunResponse> topByDistance,
            List<RunResponse> topByPace,
            Map<Bracket, List<RunResponse>> byBracket) {}
}
