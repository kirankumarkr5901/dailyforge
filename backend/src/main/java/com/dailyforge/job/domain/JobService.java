package com.dailyforge.job.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.job.repo.JobApplicationRepository;
import com.dailyforge.job.repo.JobEventRepository;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applications and their stage transitions (spec §8.7). {@code JOB_STAGE_ADVANCE} is
 * disabled by default (seeded {@code FALSE} in V2) — job activity is trivially
 * inflatable (spec §13.9), so points here are a config-gated opt-in, not the default.
 */
@Service
public class JobService {

    private final JobApplicationRepository applications;
    private final JobEventRepository events;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;
    private final ReferralProperties referralProperties;

    public JobService(
            JobApplicationRepository applications,
            JobEventRepository events,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity,
            ReferralProperties referralProperties) {
        this.applications = applications;
        this.events = events;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
        this.referralProperties = referralProperties;
    }

    @Transactional
    public JobApplication create(
            UUID userId,
            String company,
            String role,
            String roleId,
            String city,
            String jobUrl,
            String resumeVersion,
            JobSource source,
            String referrerName,
            String referralId,
            LocalDate referralRequestedOn,
            String note,
            LocalDate appliedOn) {
        JobApplication app =
                JobApplication.create(
                        userId,
                        company,
                        role,
                        roleId,
                        city,
                        jobUrl,
                        resumeVersion,
                        source,
                        referrerName,
                        referralId,
                        referralRequestedOn,
                        note,
                        appliedOn);
        applications.save(app);
        events.save(JobEvent.create(app.getId(), null, JobStatus.APPLIED, null, null, appliedOn, "Applied"));
        return app;
    }

    @Transactional
    public JobApplication update(
            UUID id,
            UUID userId,
            String company,
            String role,
            String roleId,
            String city,
            String jobUrl,
            String resumeVersion,
            String referrerName,
            String referralId,
            LocalDate referralRequestedOn,
            String note,
            LocalDate nextFollowUpOn) {
        JobApplication app = requireOwned(id, userId);
        app.update(
                company,
                role,
                roleId,
                city,
                jobUrl,
                resumeVersion,
                referrerName,
                referralId,
                referralRequestedOn,
                note,
                nextFollowUpOn);
        return applications.save(app);
    }

    @Transactional
    public JobApplication transition(
            UUID id, UUID userId, JobStatus toStatus, Integer roundNumber, InterviewStage stage, String note, LocalDate occurredOn) {
        JobApplication app = requireOwned(id, userId);
        JobStatus fromStatus = app.getStatus();

        if (toStatus == JobStatus.INTERVIEW) {
            if (stage == null) {
                throw ApiException.outOfRange("interviewStage", "Say which interview this is — technical or HR.");
            }
            if (roundNumber == null || roundNumber < 1 || roundNumber > stage.maxRound()) {
                throw ApiException.outOfRange(
                        "roundNumber", stage.label(null) + " interviews run from round 1 to " + stage.maxRound() + ".");
            }
        }

        app.transitionTo(toStatus, roundNumber, stage);
        applications.save(app);

        JobEvent event = events.save(JobEvent.create(app.getId(), fromStatus, toStatus, roundNumber, stage, occurredOn, note));

        var config = ruleConfigs.getSystemDefault("JOB_STAGE_ADVANCE");
        if (config.enabled()) {
            int amount = config.getInt("points");
            points.award(
                    new AwardCommand(
                            userId,
                            occurredOn,
                            PointsCategory.JOB,
                            "JOB_STAGE_ADVANCE",
                            amount,
                            "JOB_EVENT",
                            event.getId(),
                            app.getCompany()
                                    + " — "
                                    + (stage != null ? "Interview (" + stage.label(roundNumber) + ")" : toStatus.toString()),
                            "job-stage-advance:" + event.getId()));
        }

        return app;
    }

    /**
     * Every referral, with how long it has been waiting and what to do about it.
     *
     * A referral leaves this list when it stops being a live question — rejected or
     * withdrawn — rather than when it converts. One that turned into a real interview
     * is still a referral that worked, and seeing it is how you learn which referrers
     * are worth asking again.
     *
     * The waiting state is computed here rather than stored, in the user's own zone via
     * DayService: a stored state would be stale the moment a day passed with nobody
     * writing to the row, which for a list whose entire purpose is elapsed time would
     * be the one thing it must never get wrong.
     */
    @Transactional(readOnly = true)
    public List<Referral> referrals(UUID userId) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        return applications
                .findAllByUserIdAndSourceInOrderByReferralRequestedOnAsc(
                        userId, List.of(JobSource.REFERRAL_REQUESTED, JobSource.REFERRED))
                .stream()
                .filter(app -> app.getStatus() != JobStatus.REJECTED && app.getStatus() != JobStatus.WITHDRAWN)
                .map(
                        app ->
                                new Referral(
                                        app,
                                        referralProperties.daysWaiting(app.getReferralRequestedOn(), today),
                                        referralProperties.stateOn(app.getReferralRequestedOn(), today)))
                .toList();
    }

    /** An application, plus the two facts that only make sense for a referral. */
    public record Referral(JobApplication application, long daysWaiting, ReferralState state) {}

    @Transactional(readOnly = true)
    public List<JobApplication> list(UUID userId, JobStatus status) {
        return status != null ? applications.findAllByUserIdAndStatusOrderByAppliedOnDesc(userId, status) : applications.findAllByUserIdOrderByAppliedOnDesc(userId);
    }

    /**
     * Where a rejection came from: the stage it left, and — when that stage was an
     * interview — which interview.
     *
     * The stage and round are read from the last event *before* the rejection rather
     * than from the rejection event itself, because the rejection carries no round of
     * its own; "rejected after HR round 1" is a fact about the round that came before
     * it (owner feedback).
     */
    public record RejectionOrigin(JobStatus fromStatus, InterviewStage stage, Integer round) {}

    /**
     * For every application currently REJECTED, where that rejection came from — the
     * owner's own display-label distinction ("rejected at screening" vs. "rejected after
     * HR round 1"), computed from the timeline rather than stored as its own status.
     * Batched the same way {@link #metrics} batches its own event lookup, to keep the
     * list endpoint at one query regardless of how many applications there are.
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, RejectionOrigin> rejectionOrigins(List<JobApplication> apps) {
        List<UUID> rejectedIds = apps.stream().filter(a -> a.getStatus() == JobStatus.REJECTED).map(JobApplication::getId).toList();
        if (rejectedIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<JobEvent> allEvents = events.findAllByApplicationIdIn(rejectedIds);
        var eventsByApp = allEvents.stream().collect(java.util.stream.Collectors.groupingBy(JobEvent::getApplicationId));
        var byTime = java.util.Comparator.comparing(JobEvent::getOccurredOn).thenComparing(JobEvent::getCreatedAt);

        java.util.Map<UUID, RejectionOrigin> result = new java.util.HashMap<>();
        for (var entry : eventsByApp.entrySet()) {
            List<JobEvent> timeline = entry.getValue().stream().sorted(byTime).toList();
            for (int i = timeline.size() - 1; i >= 0; i--) {
                JobEvent event = timeline.get(i);
                if (event.getToStatus() != JobStatus.REJECTED) {
                    continue;
                }
                JobEvent previous = i > 0 ? timeline.get(i - 1) : null;
                InterviewStage stage = event.getFromStatus() == JobStatus.INTERVIEW && previous != null ? previous.getInterviewStage() : null;
                Integer round = stage != null ? previous.getRoundNumber() : null;
                result.put(entry.getKey(), new RejectionOrigin(event.getFromStatus(), stage, round));
                break;
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<JobEvent> timeline(UUID id, UUID userId) {
        JobApplication app = requireOwned(id, userId);
        return events.findAllByApplicationIdOrderByOccurredOnAscCreatedAtAsc(app.getId());
    }

    public JobApplication requireOwned(UUID id, UUID userId) {
        return applications.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("That application"));
    }

    public record Metrics(
            java.util.Map<JobStatus, Long> countsByStatus,
            double responseRate,
            Double averageDaysToFirstResponse,
            double interviewsPerApplication,
            List<JobApplication> needsFollowUp) {}

    /**
     * "Responded" means the company moved this application past the initial APPLIED
     * event — a rejection counts as a response, silence does not (spec §8.7's own
     * distinction between a pipeline and a black hole).
     */
    @Transactional(readOnly = true)
    public Metrics metrics(UUID userId) {
        List<JobApplication> apps = applications.findAllByUserIdOrderByAppliedOnDesc(userId);
        var countsByStatus =
                apps.stream().collect(java.util.stream.Collectors.groupingBy(JobApplication::getStatus, java.util.stream.Collectors.counting()));

        if (apps.isEmpty()) {
            return new Metrics(countsByStatus, 0, null, 0, List.of());
        }

        List<UUID> ids = apps.stream().map(JobApplication::getId).toList();
        List<JobEvent> allEvents = events.findAllByApplicationIdIn(ids);
        var eventsByApp =
                allEvents.stream().collect(java.util.stream.Collectors.groupingBy(JobEvent::getApplicationId));

        int responded = 0;
        long totalDaysToFirstResponse = 0;
        int respondedWithTiming = 0;
        long totalInterviewEvents = 0;

        for (JobApplication app : apps) {
            List<JobEvent> appEvents =
                    eventsByApp.getOrDefault(app.getId(), List.of()).stream()
                            .sorted(java.util.Comparator.comparing(JobEvent::getOccurredOn).thenComparing(JobEvent::getCreatedAt))
                            .toList();
            var firstResponse = appEvents.stream().filter(e -> e.getFromStatus() != null).findFirst();
            if (firstResponse.isPresent()) {
                responded++;
                long days = java.time.temporal.ChronoUnit.DAYS.between(app.getAppliedOn(), firstResponse.get().getOccurredOn());
                if (days >= 0) {
                    totalDaysToFirstResponse += days;
                    respondedWithTiming++;
                }
            }
            totalInterviewEvents += appEvents.stream().filter(e -> e.getToStatus() == JobStatus.INTERVIEW).count();
        }

        double responseRate = (double) responded / apps.size();
        Double avgDays = respondedWithTiming > 0 ? (double) totalDaysToFirstResponse / respondedWithTiming : null;
        double interviewsPerApplication = (double) totalInterviewEvents / apps.size();

        var zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);
        List<JobApplication> needsFollowUp = applications.findAllByUserIdAndNextFollowUpOnLessThanEqual(userId, today);

        return new Metrics(countsByStatus, responseRate, avgDays, interviewsPerApplication, needsFollowUp);
    }
}
