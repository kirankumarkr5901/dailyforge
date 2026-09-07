package com.dailyforge.insight.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsEntry;
import com.dailyforge.points.repo.PointsEntryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heatmap (spec §8.1.1) and a single day's detail. {@code inactiveRunLength} is
 * computed over the user's *entire* continuous history, not just the requested
 * [from, to] window — the spec is explicit that the count must carry across month
 * boundaries, which is only possible by walking from the account's actual start.
 */
@Service
public class DailySummaryService {

    private final PointsEntryRepository entries;
    private final IdentityService identity;
    private final DayService dayService;

    public DailySummaryService(PointsEntryRepository entries, IdentityService identity, DayService dayService) {
        this.entries = entries;
        this.identity = identity;
        this.dayService = dayService;
    }

    @Transactional(readOnly = true)
    public List<DailySummary> heatmap(UUID userId, LocalDate from, LocalDate to) {
        ZoneId zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);
        LocalDate accountStart = dayService.toLocalDate(identity.requireUser(userId).getCreatedAt(), zone);

        List<DailySummary> result = new ArrayList<>();

        // Dates the caller asked about that predate the account entirely.
        for (LocalDate d = from; d.isBefore(accountStart) && !d.isAfter(to); d = d.plusDays(1)) {
            result.add(DailySummary.empty(d));
        }

        LocalDate walkFrom = accountStart.isAfter(from) ? accountStart : from;
        if (!walkFrom.isAfter(to)) {
            List<PointsEntry> all =
                    entries.findAllByUserIdAndOccurredOnBetweenAndReversedFalseAndReversesIdIsNull(userId, accountStart, to);
            Map<LocalDate, List<PointsEntry>> byDate = all.stream().collect(Collectors.groupingBy(PointsEntry::getOccurredOn));

            int runLength = 0;
            for (LocalDate d = accountStart; !d.isAfter(to); d = d.plusDays(1)) {
                if (d.isAfter(today)) {
                    if (!d.isBefore(from)) {
                        result.add(DailySummary.empty(d));
                    }
                    continue; // future days never touch the streak
                }

                List<PointsEntry> dayEntries = byDate.getOrDefault(d, List.of());
                boolean hasWorkout = dayEntries.stream().anyMatch(e -> e.getCategory() == PointsCategory.WORKOUT);
                boolean hasRun = dayEntries.stream().anyMatch(e -> e.getCategory() == PointsCategory.RUN);
                boolean hasHabit =
                        dayEntries.stream().anyMatch(e -> e.getCategory() == PointsCategory.HABIT && e.getAmount() > 0);
                int pointsTotal = dayEntries.stream().mapToInt(PointsEntry::getAmount).sum();
                Map<PointsCategory, Integer> byCategory = new EnumMap<>(PointsCategory.class);
                for (PointsEntry e : dayEntries) {
                    byCategory.merge(e.getCategory(), e.getAmount(), Integer::sum);
                }

                DayState state;
                if (hasWorkout && hasRun) {
                    state = DayState.BOTH;
                    runLength = 0;
                } else if (hasWorkout) {
                    state = DayState.WORKOUT;
                    runLength = 0;
                } else if (hasRun) {
                    state = DayState.RUN;
                    runLength = 0;
                } else {
                    runLength++;
                    state = runLength <= 2 ? DayState.REST : DayState.MISSED;
                }

                if (!d.isBefore(from)) {
                    result.add(new DailySummary(d, pointsTotal, byCategory, hasWorkout, hasRun, hasHabit, runLength, state));
                }
            }
        }

        return result;
    }

    public record CategoryGroup(PointsCategory category, int total, List<PointsEntry> entries) {}

    @Transactional(readOnly = true)
    public List<CategoryGroup> dayDetail(UUID userId, LocalDate date) {
        List<PointsEntry> dayEntries =
                entries.findAllByUserIdAndOccurredOnBetweenAndReversedFalseAndReversesIdIsNull(userId, date, date);
        Map<PointsCategory, List<PointsEntry>> byCategory =
                dayEntries.stream().collect(Collectors.groupingBy(PointsEntry::getCategory, () -> new EnumMap<>(PointsCategory.class), Collectors.toList()));

        List<CategoryGroup> groups = new ArrayList<>();
        for (var entry : byCategory.entrySet()) {
            int total = entry.getValue().stream().mapToInt(PointsEntry::getAmount).sum();
            groups.add(new CategoryGroup(entry.getKey(), total, entry.getValue()));
        }
        return groups;
    }
}
