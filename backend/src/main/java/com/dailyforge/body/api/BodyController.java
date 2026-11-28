package com.dailyforge.body.api;

import com.dailyforge.body.api.BodyDtos.LogWeightRequest;
import com.dailyforge.body.api.BodyDtos.MetricResponse;
import com.dailyforge.body.api.BodyDtos.SummaryResponse;
import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Body metrics (spec §8.8). No points anywhere on this surface, per the plan. */
@RestController
@RequestMapping("/api/v1/body-metrics")
public class BodyController {

    private final BodyMetricService metrics;
    private final CurrentUser currentUser;

    private final com.dailyforge.body.domain.BodyInsightService insights;

    public BodyController(
            BodyMetricService metrics, CurrentUser currentUser, com.dailyforge.body.domain.BodyInsightService insights) {
        this.insights = insights;
        this.metrics = metrics;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<MetricResponse> list(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        UUID userId = currentUser.require();
        return metrics.list(userId, from, to).stream().map(MetricResponse::of).toList();
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return SummaryResponse.of(metrics.summary(currentUser.require()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse log(@Valid @RequestBody LogWeightRequest request) {
        UUID userId = currentUser.require();
        var metric = metrics.upsert(
                userId,
                request.date(),
                request.weightKg(),
                request.heightCm(),
                request.bodyFatPct(),
                request.note(),
                new com.dailyforge.body.domain.BodyMetric.Measurements(
                        request.waistCm(), request.chestCm(), request.armCm(), request.hipsCm(), request.thighCm(), request.neckCm()));
        return MetricResponse.of(metric);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        metrics.delete(id, currentUser.require());
    }

    public record ProfileRequest(
            com.dailyforge.body.domain.BodyInsightService.Sex sex,
            com.dailyforge.body.domain.BodyInsightService.Goal goal) {}

    /** Which body-fat formula, what the person is aiming for, and the estimates so far (last 6 months). */
    public record ProfileResponse(
            com.dailyforge.body.domain.BodyInsightService.Sex sex,
            com.dailyforge.body.domain.BodyInsightService.Goal goal,
            List<com.dailyforge.body.domain.BodyInsightService.FatPoint> bodyFat) {}

    @GetMapping("/profile")
    public ProfileResponse profile() {
        UUID userId = currentUser.require();
        var p = insights.profile(userId);
        java.time.LocalDate today = java.time.LocalDate.now();
        return new ProfileResponse(p.sex(), p.goal(), insights.bodyFat(userId, today.minusMonths(6), today.plusDays(1)));
    }

    @org.springframework.web.bind.annotation.PutMapping("/profile")
    public ProfileResponse saveProfile(@RequestBody ProfileRequest request) {
        UUID userId = currentUser.require();
        insights.saveProfile(userId, request.sex(), request.goal());
        return profile();
    }

    /** "Am I heading the right way?" from the last eight weeks against the goal; kept for the day. */
    @GetMapping("/direction")
    public com.dailyforge.body.domain.BodyInsightService.Direction direction() {
        return insights.direction(currentUser.require());
    }
}
