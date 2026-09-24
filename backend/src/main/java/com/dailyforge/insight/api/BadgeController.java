package com.dailyforge.insight.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.insight.api.InsightDtos.BadgeClaimResponse;
import com.dailyforge.insight.api.InsightDtos.BadgeResponse;
import com.dailyforge.insight.domain.BadgeService;
import com.dailyforge.insight.domain.MilestoneService.RecapPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Badges for a month or a year (owner request): what exists, how far along it is, and
 * the one action a badge ever has — taking the points for it.
 *
 * Sits under milestones' own module because a badge is a threshold over the recap that
 * module already computes, and scoring it anywhere else would risk the badge and the
 * recap disagreeing on the same page.
 */
@RestController
@RequestMapping("/api/v1/badges")
public class BadgeController {

    private final BadgeService badges;
    private final CurrentUser currentUser;
    private final DayService dayService;
    private final IdentityService identity;

    public BadgeController(BadgeService badges, CurrentUser currentUser, DayService dayService, IdentityService identity) {
        this.badges = badges;
        this.currentUser = currentUser;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** {@code date} anchors which month/year, exactly as the recap endpoint does. */
    @GetMapping
    public List<BadgeResponse> list(
            @RequestParam RecapPeriod period, @RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return badges.progress(userId, period, anchor(userId, date)).stream().map(BadgeResponse::of).toList();
    }

    /**
     * Take the points for an earned badge.
     *
     * A POST rather than a PATCH because it is an event, not an edit: it writes a
     * points entry and an award row, and asking for it twice is refused rather than
     * being a no-op that quietly pays again.
     */
    @PostMapping("/{code}/claim")
    public BadgeClaimResponse claim(@PathVariable String code, @RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return BadgeClaimResponse.of(badges.claim(userId, code, anchor(userId, date)));
    }

    private LocalDate anchor(UUID userId, LocalDate date) {
        return date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
    }
}
