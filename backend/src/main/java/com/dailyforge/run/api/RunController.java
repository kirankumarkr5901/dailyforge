package com.dailyforge.run.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.run.api.RunDtos.DeleteRunResponse;
import com.dailyforge.run.api.RunDtos.LogRunRequest;
import com.dailyforge.run.api.RunDtos.RecordsResponse;
import com.dailyforge.run.api.RunDtos.RunResponse;
import com.dailyforge.run.api.RunDtos.RunWriteResponse;
import com.dailyforge.run.domain.RunRecordsService;
import com.dailyforge.run.domain.RunService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The run tracker (spec §8.5). */
@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final RunService runService;
    private final RunRecordsService recordsService;
    private final CurrentUser currentUser;

    public RunController(RunService runService, RunRecordsService recordsService, CurrentUser currentUser) {
        this.runService = runService;
        this.recordsService = recordsService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RunResponse> list(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        UUID userId = currentUser.require();
        return runService.list(userId, from, to).stream().map(RunResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunWriteResponse log(@Valid @RequestBody LogRunRequest request) {
        UUID userId = currentUser.require();
        var write =
                runService.log(userId, request.date(), request.distanceMeters(), request.durationSeconds(), request.type(), request.note(), request.feltEffort());
        return RunWriteResponse.of(write.run(), write.points());
    }

    @PatchMapping("/{id}")
    public RunWriteResponse update(@PathVariable UUID id, @Valid @RequestBody LogRunRequest request) {
        UUID userId = currentUser.require();
        var write =
                runService.update(userId, id, request.distanceMeters(), request.durationSeconds(), request.type(), request.note(), request.feltEffort());
        return RunWriteResponse.of(write.run(), write.points());
    }

    @DeleteMapping("/{id}")
    public DeleteRunResponse delete(@PathVariable UUID id) {
        UUID userId = currentUser.require();
        PointsResult result = runService.delete(userId, id);
        return DeleteRunResponse.of(result);
    }

    @GetMapping("/records")
    public RecordsResponse records() {
        UUID userId = currentUser.require();
        Map<RunRecordsService.Bracket, List<RunResponse>> byBracket =
                recordsService.byBracket(userId, 3).entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue().stream().map(RunResponse::of).toList(),
                                        (a, b) -> a,
                                        java.util.LinkedHashMap::new));
        return new RecordsResponse(
                recordsService.lifetimeRunPoints(userId),
                recordsService.topByDistance(userId, 3).stream().map(RunResponse::of).toList(),
                recordsService.topByPace(userId, 3).stream().map(RunResponse::of).toList(),
                byBracket);
    }
}
