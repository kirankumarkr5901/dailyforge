package com.dailyforge.insight.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.insight.api.InsightDtos.QuoteResponse;
import com.dailyforge.insight.domain.QuoteService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The standalone quote endpoint spec §7 lists — Home embeds the same value in its own summary call. */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quotes;
    private final CurrentUser currentUser;
    private final DayService dayService;
    private final IdentityService identity;

    public QuoteController(QuoteService quotes, CurrentUser currentUser, DayService dayService, IdentityService identity) {
        this.quotes = quotes;
        this.currentUser = currentUser;
        this.dayService = dayService;
        this.identity = identity;
    }

    @GetMapping("/today")
    public QuoteResponse today() {
        UUID userId = currentUser.require();
        var zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        return QuoteResponse.of(quotes.forDate(userId, dayService.today(zone)));
    }
}
