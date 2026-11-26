package com.dailyforge.nutrition.domain;

import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A day whose water reached the target holds one {@code WATER_TARGET} entry; any other day, none. */
@Component
public class WaterDayReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.WaterDay> {

    public static final String SOURCE_TYPE = "WATER_DAY";
    public static final String RULE_CODE = "WATER_TARGET";

    private final JdbcTemplate jdbc;
    private final NutritionSettingsRepository settings;
    private final PointsRuleConfigService ruleConfigs;

    public WaterDayReconciliationCalculator(
            JdbcTemplate jdbc, NutritionSettingsRepository settings, PointsRuleConfigService ruleConfigs) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.ruleConfigs = ruleConfigs;
    }

    public static UUID sourceIdFor(UUID userId, LocalDate date) {
        return UUID.nameUUIDFromBytes(("water-day:" + userId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Class<ReconcileScope.WaterDay> scopeType() {
        return ReconcileScope.WaterDay.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.WaterDay scope) {
        var config = ruleConfigs.getSystemDefault(RULE_CODE);
        int amount = config.enabled() ? config.getInt("points") : 0;
        if (amount <= 0) {
            return List.of();
        }
        int target = settings.findById(scope.userId())
                .map(NutritionSettings::getWaterTargetMl)
                .orElse(NutritionSettings.DEFAULT_WATER_TARGET_ML);
        List<Integer> ml = jdbc.queryForList(
                "SELECT ml FROM water_log WHERE user_id = ? AND occurred_on = ?", Integer.class, scope.userId(), scope.date());
        if (ml.isEmpty() || ml.get(0) < target) {
            return List.of();
        }
        return List.of(new DesiredEntry(
                scope.date(),
                PointsCategory.NUTRITION,
                RULE_CODE,
                amount,
                SOURCE_TYPE,
                sourceIdFor(scope.userId(), scope.date()),
                "Water target reached"));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.WaterDay scope) {
        return Set.of(sourceIdFor(scope.userId(), scope.date()));
    }
}
