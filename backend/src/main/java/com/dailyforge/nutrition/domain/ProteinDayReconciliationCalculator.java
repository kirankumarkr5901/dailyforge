package com.dailyforge.nutrition.domain;

import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import com.dailyforge.nutrition.repo.ProteinLogRepository;
import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A day whose protein reached the target should hold exactly one {@code PROTEIN_TARGET}
 * entry; a day below it, none. Computed from the day's logs, never from the ledger.
 */
@Component
public class ProteinDayReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.ProteinDay> {

    public static final String SOURCE_TYPE = "PROTEIN_DAY";
    public static final String RULE_CODE = "PROTEIN_TARGET";

    private final ProteinLogRepository logs;
    private final NutritionSettingsRepository settings;
    private final PointsRuleConfigService ruleConfigs;

    public ProteinDayReconciliationCalculator(
            ProteinLogRepository logs, NutritionSettingsRepository settings, PointsRuleConfigService ruleConfigs) {
        this.logs = logs;
        this.settings = settings;
        this.ruleConfigs = ruleConfigs;
    }

    /** A day has no row of its own, so its source id is derived from the user and the date. */
    public static UUID sourceIdFor(UUID userId, LocalDate date) {
        return UUID.nameUUIDFromBytes(("protein-day:" + userId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Class<ReconcileScope.ProteinDay> scopeType() {
        return ReconcileScope.ProteinDay.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.ProteinDay scope) {
        var config = ruleConfigs.getSystemDefault(RULE_CODE);
        if (!config.enabled()) {
            return List.of();
        }
        int target = settings.findById(scope.userId())
                .map(NutritionSettings::getProteinTargetG)
                .orElse(NutritionSettings.DEFAULT_TARGET_G);
        int total = logs.findAllByUserIdAndOccurredOnOrderByCreatedAtAsc(scope.userId(), scope.date()).stream()
                .mapToInt(ProteinLog::getGrams)
                .sum();
        int amount = config.getInt("points");
        if (total < target || amount <= 0) {
            return List.of();
        }
        return List.of(new DesiredEntry(
                scope.date(),
                PointsCategory.NUTRITION,
                RULE_CODE,
                amount,
                SOURCE_TYPE,
                sourceIdFor(scope.userId(), scope.date()),
                // Fixed text: the total keeps changing as you eat, and the entry itself should not.
                "Protein target hit"));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.ProteinDay scope) {
        return Set.of(sourceIdFor(scope.userId(), scope.date()));
    }
}
