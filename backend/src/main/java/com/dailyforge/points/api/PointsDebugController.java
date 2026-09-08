package com.dailyforge.points.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a signed-in user award themselves arbitrary test points on demand — precisely
 * what spec §5.7's anti-gaming guardrails exist to prevent. This bean is never created
 * in production: {@code @Profile("!prod")} means it is absent from the production
 * binary's context, not merely hidden behind a check that a misconfiguration could
 * disable.
 *
 * M2's own words: "No UI beyond a debug page." This is that page's only endpoint; the
 * real, safe endpoints (ledger, snapshot, recalculate) live in {@link PointsController}
 * and ship everywhere.
 */
@RestController
@RequestMapping("/api/v1/points/debug")
@Profile("!prod")
public class PointsDebugController {

    private final PointsService points;
    private final IdentityService identity;
    private final DayService dayService;
    private final CurrentUser currentUser;

    public PointsDebugController(
            PointsService points, IdentityService identity, DayService dayService, CurrentUser currentUser) {
        this.points = points;
        this.identity = identity;
        this.dayService = dayService;
        this.currentUser = currentUser;
    }

    public record DebugAwardRequest(
            @NotNull PointsCategory category,
            @NotBlank String ruleCode,
            @NotNull Integer amount,
            @NotBlank String description) {}

    @PostMapping("/award")
    public PointsEnvelope award(@Valid @RequestBody DebugAwardRequest request) {
        UUID userId = currentUser.require();
        var zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        var today = dayService.today(zone);

        PointsResult result =
                points.award(
                        AwardCommand.withoutSource(
                                userId,
                                today,
                                request.category(),
                                request.ruleCode(),
                                request.amount(),
                                request.description(),
                                // A fresh key every call: this is a manual test button, not a
                                // retry-safe user action, so each click is meant to award again.
                                "debug:" + UUID.randomUUID()));

        return PointsEnvelope.of(result);
    }
}
