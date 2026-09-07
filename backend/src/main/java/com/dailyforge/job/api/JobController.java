package com.dailyforge.job.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.job.api.JobDtos.ApplicationResponse;
import com.dailyforge.job.api.JobDtos.CreateApplicationRequest;
import com.dailyforge.job.api.JobDtos.EventResponse;
import com.dailyforge.job.api.JobDtos.MetricsResponse;
import com.dailyforge.job.api.JobDtos.TransitionRequest;
import com.dailyforge.job.api.JobDtos.UpdateApplicationRequest;
import com.dailyforge.job.domain.JobService;
import com.dailyforge.job.domain.JobStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The job pipeline (spec §8.7). */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobs;
    private final CurrentUser currentUser;

    public JobController(JobService jobs, CurrentUser currentUser) {
        this.jobs = jobs;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ApplicationResponse> list(@RequestParam(required = false) JobStatus status) {
        var apps = jobs.list(currentUser.require(), status);
        var rejectedFrom = jobs.rejectedFromStatuses(apps);
        return apps.stream().map(app -> ApplicationResponse.of(app, rejectedFrom.get(app.getId()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request) {
        var app =
                jobs.create(
                        currentUser.require(),
                        request.company().trim(),
                        request.role().trim(),
                        request.roleId(),
                        request.city(),
                        request.jobUrl(),
                        request.resumeVersion(),
                        request.source(),
                        request.referrerName(),
                        request.note(),
                        request.appliedOn());
        return ApplicationResponse.of(app);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateApplicationRequest request) {
        var app =
                jobs.update(
                        id,
                        currentUser.require(),
                        request.company(),
                        request.role(),
                        request.roleId(),
                        request.city(),
                        request.jobUrl(),
                        request.resumeVersion(),
                        request.referrerName(),
                        request.note(),
                        request.nextFollowUpOn());
        return ApplicationResponse.of(app);
    }

    @PostMapping("/{id}/transition")
    public ApplicationResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        var app = jobs.transition(id, currentUser.require(), request.toStatus(), request.roundNumber(), request.note(), request.occurredOn());
        return ApplicationResponse.of(app);
    }

    @GetMapping("/{id}/timeline")
    public List<EventResponse> timeline(@PathVariable UUID id) {
        return jobs.timeline(id, currentUser.require()).stream().map(EventResponse::of).toList();
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        return MetricsResponse.of(jobs.metrics(currentUser.require()));
    }
}
