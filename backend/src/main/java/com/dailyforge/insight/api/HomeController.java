package com.dailyforge.insight.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.insight.api.InsightDtos.HomeSummaryResponse;
import com.dailyforge.insight.domain.HomeSummaryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Home page in one call (spec §8.1). */
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeSummaryService summaryService;
    private final CurrentUser currentUser;
    private final DayService dayService;
    private final IdentityService identity;

    public HomeController(HomeSummaryService summaryService, CurrentUser currentUser, DayService dayService, IdentityService identity) {
        this.summaryService = summaryService;
        this.currentUser = currentUser;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** {@code date} is optional, resolving to the server's own "today" — same contract as the habit and workout boards. */
    @GetMapping("/summary")
    public HomeSummaryResponse summary(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return HomeSummaryResponse.of(summaryService.summary(userId, resolved));
    }
}
