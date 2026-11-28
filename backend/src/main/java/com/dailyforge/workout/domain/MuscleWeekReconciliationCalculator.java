package com.dailyforge.workout.domain;

import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import com.dailyforge.points.domain.RuleConfig;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Weekly set targets pay: each main muscle that reaches its minimum sets in a
 * Monday-to-Sunday week pays {@code MUSCLE_TARGET} (5), and every main muscle reaching
 * it pays {@code MUSCLE_TARGET_ALL} (25) on top. Recomputed from the week's sets, so
 * deleting sets below a minimum takes the points back. Dated the week's Monday, so an
 * entry never moves between days.
 */
@Component
public class MuscleWeekReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.MuscleWeek> {

    static final String SOURCE_TYPE = "MUSCLE_WEEK";

    private final WeeklyVolumeService volume;
    private final MuscleTargetService targets;
    private final PointsRuleConfigService ruleConfigs;

    public MuscleWeekReconciliationCalculator(
            WeeklyVolumeService volume, MuscleTargetService targets, PointsRuleConfigService ruleConfigs) {
        this.volume = volume;
        this.targets = targets;
        this.ruleConfigs = ruleConfigs;
    }

    @Override
    public Class<ReconcileScope.MuscleWeek> scopeType() {
        return ReconcileScope.MuscleWeek.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.MuscleWeek scope) {
        LocalDate week = scope.weekStart();
        Map<String, Integer> done = volume.forWeekOf(scope.userId(), week).muscles().stream()
                .collect(Collectors.toMap(WeeklyVolumeService.MuscleSets::group, WeeklyVolumeService.MuscleSets::sets));
        Map<String, MuscleTargetService.Target> mine = targets.current(scope.userId());

        RuleConfig each = ruleConfigs.getSystemDefault("MUSCLE_TARGET");
        RuleConfig all = ruleConfigs.getSystemDefault("MUSCLE_TARGET_ALL");
        List<DesiredEntry> out = new ArrayList<>();
        int reached = 0;
        for (String m : MainMuscles.ALL) {
            if (done.getOrDefault(m, 0) >= mine.get(m).min()) {
                reached++;
                if (each.enabled() && each.getInt("points") > 0) {
                    out.add(new DesiredEntry(week, PointsCategory.WORKOUT, "MUSCLE_TARGET", each.getInt("points"),
                            SOURCE_TYPE, idFor(scope.userId(), week, m), "Weekly sets hit: " + m));
                }
            }
        }
        if (reached == MainMuscles.ALL.size() && all.enabled() && all.getInt("points") > 0) {
            out.add(new DesiredEntry(week, PointsCategory.WORKOUT, "MUSCLE_TARGET_ALL", all.getInt("points"),
                    SOURCE_TYPE, idFor(scope.userId(), week, "ALL"), "Every muscle hit its weekly sets"));
        }
        return out;
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.MuscleWeek scope) {
        Set<UUID> ids = new HashSet<>();
        MainMuscles.ALL.forEach(m -> ids.add(idFor(scope.userId(), scope.weekStart(), m)));
        ids.add(idFor(scope.userId(), scope.weekStart(), "ALL"));
        return ids;
    }

    /** One stable id per user, week and muscle, so a muscle pays once per week. */
    static UUID idFor(UUID userId, LocalDate week, String muscle) {
        return UUID.nameUUIDFromBytes((userId + "|" + week + "|" + muscle).getBytes(StandardCharsets.UTF_8));
    }
}
