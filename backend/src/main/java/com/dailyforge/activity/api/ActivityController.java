package com.dailyforge.activity.api;

import com.dailyforge.activity.api.ActivityDtos.ActivityLogResponse;
import com.dailyforge.activity.api.ActivityDtos.ActivityTypeResponse;
import com.dailyforge.activity.api.ActivityDtos.CreateActivityTypeRequest;
import com.dailyforge.activity.api.ActivityDtos.DeleteLogResponse;
import com.dailyforge.activity.api.ActivityDtos.LogActivityRequest;
import com.dailyforge.activity.api.ActivityDtos.LogWriteResponse;
import com.dailyforge.activity.domain.ActivityService;
import com.dailyforge.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Positive and negative one-off activities (spec §6 "activity"). */
@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    private final ActivityService activities;
    private final CurrentUser currentUser;

    public ActivityController(ActivityService activities, CurrentUser currentUser) {
        this.activities = activities;
        this.currentUser = currentUser;
    }

    @GetMapping("/activities")
    public List<ActivityTypeResponse> list() {
        return activities.list(currentUser.require()).stream().map(ActivityTypeResponse::of).toList();
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityTypeResponse create(@Valid @RequestBody CreateActivityTypeRequest request) {
        var type =
                activities.create(currentUser.require(), request.name().trim(), request.polarity(), request.points(), request.icon());
        return ActivityTypeResponse.of(type);
    }

    @DeleteMapping("/activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        activities.archive(id, currentUser.require());
    }

    @GetMapping("/activity-logs")
    public List<ActivityLogResponse> recentLogs() {
        return activities.recentLogs(currentUser.require()).stream().map(ActivityLogResponse::of).toList();
    }

    @PostMapping("/activities/{id}/logs")
    public LogWriteResponse log(@PathVariable UUID id, @Valid @RequestBody LogActivityRequest request) {
        var result =
                activities.log(id, currentUser.require(), request.date(), request.count() != null ? request.count() : 1, request.note());
        return LogWriteResponse.of(result.log(), result.points());
    }

    @DeleteMapping("/activity-logs/{id}")
    public DeleteLogResponse deleteLog(@PathVariable UUID id) {
        return DeleteLogResponse.of(activities.deleteLog(id, currentUser.require()));
    }
}
