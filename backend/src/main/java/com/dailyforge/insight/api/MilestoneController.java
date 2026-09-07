package com.dailyforge.insight.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.insight.api.InsightDtos.MilestoneRecapResponse;
import com.dailyforge.insight.domain.MilestoneService;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Automatic monthly/yearly recaps — the auto-generated half of "milestones for every month, yearly" (owner feedback). */
@RestController
@RequestMapping("/api/v1/milestones")
public class MilestoneController {

    private final MilestoneService milestones;
    private final CurrentUser currentUser;
    private final DayService dayService;
    private final IdentityService identity;

    public MilestoneController(MilestoneService milestones, CurrentUser currentUser, DayService dayService, IdentityService identity) {
        this.milestones = milestones;
        this.currentUser = currentUser;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** {@code date} anchors which month/year; omitted resolves to the server's own "today", same contract as every other board. */
    @GetMapping("/recap")
    public MilestoneRecapResponse recap(
            @RequestParam RecapPeriod period, @RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return MilestoneRecapResponse.of(milestones.recap(userId, period, resolved));
    }
}
