package com.dailyforge.forge.domain;

import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A beaten Forge 66 boss pays its XP into the main score at the same rate as a quest;
 * one un-beaten again (a quest unmarked the same day) has its entry reversed.
 */
@Component
public class ForgeBossReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.ForgeBoss> {

    public static final String SOURCE_TYPE = "FORGE_BOSS";
    public static final String RULE_CODE = "FORGE_BOSS";

    // Reads the boss row itself: going through ForgeBossService would loop back into the
    // points engine that loads this calculator.
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ForgeSettingsProvider settings;

    public ForgeBossReconciliationCalculator(org.springframework.jdbc.core.JdbcTemplate jdbc, ForgeSettingsProvider settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    @Override
    public Class<ReconcileScope.ForgeBoss> scopeType() {
        return ReconcileScope.ForgeBoss.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.ForgeBoss scope) {
        var boss = jdbc.query("SELECT * FROM forge_boss WHERE id = ?", ForgeBossService.ROW, scope.bossId())
                .stream()
                .findFirst()
                .orElse(null);
        if (boss == null || boss.status() != ForgeBossService.Status.DEFEATED || boss.defeatedOn() == null) {
            return List.of();
        }
        int amount = settings.current().pointsFor(boss.rewardXp());
        if (amount <= 0) {
            return List.of();
        }
        return List.of(new DesiredEntry(
                boss.defeatedOn(),
                PointsCategory.FORGE,
                RULE_CODE,
                amount,
                SOURCE_TYPE,
                boss.id(),
                "Forge 66 boss beaten: " + boss.name()));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.ForgeBoss scope) {
        return Set.of(scope.bossId());
    }
}
