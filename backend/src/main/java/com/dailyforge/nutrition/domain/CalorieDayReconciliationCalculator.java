package com.dailyforge.nutrition.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
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
 * A day that is over, had something logged, and stayed within the calorie budget holds
 * one {@code CALORIE_BUDGET} entry; any other day, none.
 *
 * <p>"Within budget" can only be judged once the day is over — at lunch every day is
 * within budget — so today never pays. The daily rollover asks for the day just closed
 * ({@link CalorieRolloverParticipant}); an edit to a past day inside the edit window
 * asks again, so the answer follows the logs. A day with nothing logged pays nothing:
 * an empty log is not a day eaten within budget.
 */
@Component
public class CalorieDayReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.CalorieDay> {

    public static final String SOURCE_TYPE = "CALORIE_DAY";
    public static final String RULE_CODE = "CALORIE_BUDGET";

    private final ProteinLogRepository logs;
    private final NutritionSettingsRepository settings;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public CalorieDayReconciliationCalculator(
            ProteinLogRepository logs,
            NutritionSettingsRepository settings,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.logs = logs;
        this.settings = settings;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    public static UUID sourceIdFor(UUID userId, LocalDate date) {
        return UUID.nameUUIDFromBytes(("calorie-day:" + userId + ":" + date).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Class<ReconcileScope.CalorieDay> scopeType() {
        return ReconcileScope.CalorieDay.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.CalorieDay scope) {
        var config = ruleConfigs.getSystemDefault(RULE_CODE);
        int amount = config.enabled() ? config.getInt("points") : 0;
        LocalDate today = dayService.today(dayService.zoneOf(identity.requireSettings(scope.userId()).getTimeZone()));
        if (amount <= 0 || !scope.date().isBefore(today)) {
            return List.of();
        }
        List<ProteinLog> dayLogs = logs.findAllByUserIdAndOccurredOnOrderByCreatedAtAsc(scope.userId(), scope.date());
        if (dayLogs.isEmpty()) {
            return List.of();
        }
        int budget = settings.findById(scope.userId())
                .map(NutritionSettings::getCalorieBudget)
                .orElse(NutritionSettings.DEFAULT_CALORIE_BUDGET);
        int total = dayLogs.stream().mapToInt(ProteinLog::getKcal).sum();
        if (total > budget) {
            return List.of();
        }
        return List.of(new DesiredEntry(
                scope.date(),
                PointsCategory.NUTRITION,
                RULE_CODE,
                amount,
                SOURCE_TYPE,
                sourceIdFor(scope.userId(), scope.date()),
                "Within the calorie budget"));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.CalorieDay scope) {
        return Set.of(sourceIdFor(scope.userId(), scope.date()));
    }
}
