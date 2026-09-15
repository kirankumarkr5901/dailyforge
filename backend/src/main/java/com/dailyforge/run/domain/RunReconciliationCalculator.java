package com.dailyforge.run.domain;

import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import com.dailyforge.points.domain.RuleConfig;
import com.dailyforge.run.repo.RunRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Recomputes one user's entire run history from scratch (spec §5.3): every run's own
 * {@code RUN_DISTANCE} points, plus {@code RUN_MILESTONE} for the highest single
 * threshold each run crosses (§13.3 — stacking within one run is disabled by default),
 * plus {@code RUN_FIRST_MILESTONE} for whichever run was the first, in chronological
 * order, to reach at least that far.
 *
 * "First ever" is read here as first ever to reach *at least* that distance, not merely
 * the first run individually labelled with that exact milestone — a 42.2 km run is also
 * the first-ever 10 km+ run if no earlier run reached 10 km, even though its own
 * label is the 42.2 km milestone. Recomputing from the full history on every change is
 * what makes this correct when a run is edited or deleted: deleting the historically
 * first run to cross a threshold correctly promotes the next-earliest one, because nothing
 * here is read from the ledger itself, only from the raw {@code run} rows that remain.
 */
@Component
public class RunReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.RunPr> {

    static final String SOURCE_TYPE = "RUN_LOG";

    private final RunRepository runs;
    private final PointsRuleConfigService ruleConfigs;

    public RunReconciliationCalculator(RunRepository runs, PointsRuleConfigService ruleConfigs) {
        this.runs = runs;
        this.ruleConfigs = ruleConfigs;
    }

    @Override
    public Class<ReconcileScope.RunPr> scopeType() {
        return ReconcileScope.RunPr.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.RunPr scope) {
        List<Run> chronological = runs.findAllByUserIdAndDeletedAtIsNullOrderByOccurredOnAscCreatedAtAsc(scope.userId());
        List<DesiredEntry> desired = new ArrayList<>();

        RuleConfig distanceConfig = ruleConfigs.getSystemDefault("RUN_DISTANCE");
        RuleConfig milestoneConfig = ruleConfigs.getSystemDefault("RUN_MILESTONE");
        RuleConfig firstConfig = ruleConfigs.getSystemDefault("RUN_FIRST_MILESTONE");

        List<Milestone> milestones = milestoneConfig.enabled() ? parseMilestones(milestoneConfig) : List.of();
        int metresPerPoint = distanceConfig.enabled() ? distanceConfig.getInt("metresPerPoint") : 0;
        double firstMultiplier = firstConfig.enabled() ? firstConfig.getDouble("multiplier") : 0;

        int furthestMilestoneSoFar = 0; // metres; the high-water mark "first ever" is measured against

        for (Run run : chronological) {
            if (distanceConfig.enabled()) {
                int points = run.getDistanceMeters() / metresPerPoint;
                desired.add(
                        new DesiredEntry(
                                run.getOccurredOn(), PointsCategory.RUN, "RUN_DISTANCE", points, SOURCE_TYPE, run.getId(), describeRun(run)));
            }

            Milestone reached = highestReached(milestones, run.getDistanceMeters());
            if (reached != null && milestoneConfig.enabled()) {
                desired.add(
                        new DesiredEntry(
                                run.getOccurredOn(),
                                PointsCategory.RUN,
                                "RUN_MILESTONE",
                                reached.points(),
                                SOURCE_TYPE,
                                run.getId(),
                                reached.metres() / 1000.0 + " km milestone"));

                if (reached.metres() > furthestMilestoneSoFar && firstConfig.enabled()) {
                    desired.add(
                            new DesiredEntry(
                                    run.getOccurredOn(),
                                    PointsCategory.RUN,
                                    "RUN_FIRST_MILESTONE",
                                    (int) Math.round(reached.points() * firstMultiplier),
                                    SOURCE_TYPE,
                                    run.getId(),
                                    "First time reaching " + reached.metres() / 1000.0 + " km"));
                }
                furthestMilestoneSoFar = Math.max(furthestMilestoneSoFar, reached.metres());
            }
        }

        return desired;
    }

    private String describeRun(Run run) {
        return run.getDistanceMeters() / 1000.0 + " km run";
    }

    /** Highest configured threshold this run's own distance reaches, or null. */
    private Milestone highestReached(List<Milestone> milestones, int distanceMeters) {
        return milestones.stream()
                .filter(m -> distanceMeters >= m.metres())
                .max(Comparator.comparingInt(Milestone::metres))
                .orElse(null);
    }

    private List<Milestone> parseMilestones(RuleConfig config) {
        JsonNode array = config.getNode("milestones");
        List<Milestone> milestones = new ArrayList<>();
        array.forEach(node -> milestones.add(new Milestone(node.get("metres").asInt(), node.get("points").asInt())));
        return milestones;
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.RunPr scope) {
        // Every run ever, deleted or not — a deleted run's stale entries must stay
        // reachable to reverse (see the migration's own note on why runs are
        // soft-deleted, and HabitReconciliationCalculator's identical reasoning).
        return runs.findAllByUserId(scope.userId()).stream().map(Run::getId).collect(Collectors.toSet());
    }

    private record Milestone(int metres, int points) {}
}
