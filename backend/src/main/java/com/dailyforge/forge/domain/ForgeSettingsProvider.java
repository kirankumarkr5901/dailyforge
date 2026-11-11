package com.dailyforge.forge.domain;

import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.RuleConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Parses the two Forge 66 rows of {@code points_rule_config} into {@link ForgeSettings}. */
@Component
public class ForgeSettingsProvider {

    private final PointsRuleConfigService ruleConfigs;

    public ForgeSettingsProvider(PointsRuleConfigService ruleConfigs) {
        this.ruleConfigs = ruleConfigs;
    }

    public ForgeSettings current() {
        RuleConfig progression = ruleConfigs.getSystemDefault("FORGE_PROGRESSION");
        RuleConfig quest = ruleConfigs.getSystemDefault("FORGE_QUEST");

        List<Integer> questsPerPhase = new ArrayList<>();
        for (JsonNode n : progression.getNode("questsPerPhase")) {
            questsPerPhase.add(n.asInt());
        }

        Map<Integer, Integer> streakBonus = new HashMap<>();
        JsonNode bonusNode = progression.getNode("streakBonusXp");
        for (String key : bonusNode.propertyNames()) {
            streakBonus.put(Integer.parseInt(key), bonusNode.get(key).asInt());
        }

        return new ForgeSettings(
                progression.getInt("lengthDays"),
                questsPerPhase,
                progression.getInt("focusAreas"),
                progression.getInt("levelBase"),
                progression.getDouble("levelExponent"),
                progression.getDouble("hardModeXpMultiplier"),
                progression.getInt("dayCompleteXp"),
                progression.getInt("hardModePenaltyXp"),
                streakBonus,
                quest.enabled() ? quest.getDouble("pointsPerXp") : 0.0);
    }
}
