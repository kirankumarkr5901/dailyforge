package com.dailyforge.job.api;

import com.dailyforge.job.domain.JobApplication;
import com.dailyforge.job.domain.JobEvent;
import com.dailyforge.job.domain.JobService.Metrics;
import com.dailyforge.job.domain.JobSource;
import com.dailyforge.job.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {}

    public record CreateApplicationRequest(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Size(max = 120) String role,
            @Size(max = 80) String roleId,
            @Size(max = 120) String city,
            @Size(max = 500) String jobUrl,
            @Size(max = 80) String resumeVersion,
            @NotNull JobSource source,
            @Size(max = 120) String referrerName,
            @Size(max = 1000) String note,
            @NotNull LocalDate appliedOn) {}

    public record UpdateApplicationRequest(
            @Size(max = 120) String company,
            @Size(max = 120) String role,
            @Size(max = 80) String roleId,
            @Size(max = 120) String city,
            @Size(max = 500) String jobUrl,
            @Size(max = 80) String resumeVersion,
            @Size(max = 120) String referrerName,
            @Size(max = 1000) String note,
            LocalDate nextFollowUpOn) {}

    public record TransitionRequest(@NotNull JobStatus toStatus, Integer roundNumber, String note, @NotNull LocalDate occurredOn) {}

    public record ApplicationResponse(
            UUID id,
            String company,
            String role,
            String roleId,
            String city,
            String jobUrl,
            String resumeVersion,
            JobSource source,
            String referrerName,
            JobStatus status,
            int currentRound,
            LocalDate nextFollowUpOn,
            String note,
            LocalDate appliedOn,
            /** Only set when status is REJECTED — the stage the rejection came from, for
             * a display label like "Rejected at screening" (owner feedback), never a
             * status of its own. */
            JobStatus rejectedFromStatus) {

        public static ApplicationResponse of(JobApplication app) {
            return of(app, null);
        }

        public static ApplicationResponse of(JobApplication app, JobStatus rejectedFromStatus) {
            return new ApplicationResponse(
                    app.getId(),
                    app.getCompany(),
                    app.getRole(),
                    app.getRoleId(),
                    app.getCity(),
                    app.getJobUrl(),
                    app.getResumeVersion(),
                    app.getSource(),
                    app.getReferrerName(),
                    app.getStatus(),
                    app.getCurrentRound(),
                    app.getNextFollowUpOn(),
                    app.getNote(),
                    app.getAppliedOn(),
                    rejectedFromStatus);
        }
    }

    public record EventResponse(UUID id, JobStatus fromStatus, JobStatus toStatus, Integer roundNumber, LocalDate occurredOn, String note) {
        public static EventResponse of(JobEvent event) {
            return new EventResponse(
                    event.getId(), event.getFromStatus(), event.getToStatus(), event.getRoundNumber(), event.getOccurredOn(), event.getNote());
        }
    }

    public record MetricsResponse(
            Map<JobStatus, Long> countsByStatus,
            double responseRate,
            Double averageDaysToFirstResponse,
            double interviewsPerApplication,
            List<ApplicationResponse> needsFollowUp) {

        public static MetricsResponse of(Metrics metrics) {
            return new MetricsResponse(
                    metrics.countsByStatus(),
                    metrics.responseRate(),
                    metrics.averageDaysToFirstResponse(),
                    metrics.interviewsPerApplication(),
                    metrics.needsFollowUp().stream().map(ApplicationResponse::of).toList());
        }
    }
}
