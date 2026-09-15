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

    private final DailySummaryService summaries;
    private final CurrentUser currentUser;

    public InsightsController(DailySummaryService summaries, CurrentUser currentUser) {
        this.summaries = summaries;
        this.currentUser = currentUser;
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
