package com.dailyforge.insight.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.insight.api.InsightDtos.CategoryGroupResponse;
import com.dailyforge.insight.api.InsightDtos.DailySummaryResponse;
import com.dailyforge.insight.api.InsightDtos.DayDetailResponse;
import com.dailyforge.insight.domain.DailySummaryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The heatmap and its day-detail sheet (spec §8.1.1). */
@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

    private final com.dailyforge.insight.domain.WeeklyCoachService weeklyCoach;
    private final DailySummaryService summaries;
    private final CurrentUser currentUser;
    private final com.dailyforge.insight.domain.WeekReviewService weekReviews;

    public InsightsController(
            DailySummaryService summaries,
            CurrentUser currentUser,
            com.dailyforge.insight.domain.WeekReviewService weekReviews,
            com.dailyforge.insight.domain.WeeklyCoachService weeklyCoach) {
        this.summaries = summaries;
        this.currentUser = currentUser;
        this.weekReviews = weekReviews;
        this.weeklyCoach = weeklyCoach;
    }

    /**
     * The AI coach's review of the week containing {@code date}: written once that week
     * reaches its Sunday, then kept. 204 when AI is off or the week isn't there yet.
     */
    @GetMapping("/coach")
    public org.springframework.http.ResponseEntity<com.dailyforge.insight.domain.WeeklyCoachService.Review> coach(
            @RequestParam LocalDate date) {
        var review = weeklyCoach.review(currentUser.require(), date);
        return review == null
                ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(review);
    }

    /** The Monday-to-Sunday week containing {@code date} (this week if omitted), in a few numbers. */
    @GetMapping("/week")
    public com.dailyforge.insight.domain.WeekReviewService.WeekReview week(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return weekReviews.review(userId, date != null ? date : weekReviews.today(userId));
    }

    @GetMapping("/heatmap")
    public List<DailySummaryResponse> heatmap(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        UUID userId = currentUser.require();
        return summaries.heatmap(userId, from, to).stream().map(DailySummaryResponse::of).toList();
    }

    @GetMapping("/day/{date}")
    public DayDetailResponse day(@PathVariable LocalDate date) {
        UUID userId = currentUser.require();
        var groups = summaries.dayDetail(userId, date).stream().map(CategoryGroupResponse::of).toList();
        int total = groups.stream().mapToInt(CategoryGroupResponse::total).sum();
        return new DayDetailResponse(date, total, groups);
    }
}
