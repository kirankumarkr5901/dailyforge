package com.dailyforge.insight.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.goal.domain.Goal;
import com.dailyforge.goal.domain.GoalService;
import com.dailyforge.goal.domain.GoalStatus;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsEntry;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ScoreSnapshot;
import com.dailyforge.points.repo.PointsEntryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles {@code GET /home/summary} (spec §8.1) in one call: quote, score, active
 * goals, and the recent ledger. Job metrics are still omitted entirely rather than sent
 * empty (spec's own "hidden entirely when none exist" rule, which an absent field
 * already satisfies) — active goals were the one owner feedback specifically named as
 * missing, so this round only fills in that one.
 */
@Service
public class HomeSummaryService {

    private final QuoteService quotes;
    private final PointsService points;
    private final PointsEntryRepository entries;
    private final GoalService goalService;
    private final DayService dayService;
    private final IdentityService identity;

    public HomeSummaryService(
            QuoteService quotes,
            PointsService points,
            PointsEntryRepository entries,
            GoalService goalService,
            DayService dayService,
            IdentityService identity) {
        this.quotes = quotes;
        this.points = points;
        this.entries = entries;
        this.goalService = goalService;
        this.dayService = dayService;
        this.identity = identity;
    }

    public record HomeSummary(
            LocalDate date, Quote quote, ScoreSnapshot score, List<Goal> activeGoals, List<PointsEntry> recentLedger) {}

    @Transactional(readOnly = true)
    public HomeSummary summary(UUID userId, LocalDate date) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        Quote quote = quotes.forDate(userId, date);
        ScoreSnapshot score = points.snapshot(userId, zone);
        List<Goal> activeGoals = goalService.list(userId, GoalStatus.ACTIVE);
        List<PointsEntry> recentLedger =
                entries.findAllByUserIdOrderByOccurredOnDescCreatedAtDesc(userId, PageRequest.of(0, 20)).getContent();
        return new HomeSummary(date, quote, score, activeGoals, recentLedger);
    }
}
