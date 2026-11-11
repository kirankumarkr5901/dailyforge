package com.dailyforge.forge.domain;

import com.dailyforge.forge.repo.ForgeQuestRepository;
import com.dailyforge.forge.repo.ForgeQuestTemplateRepository;
import com.dailyforge.points.domain.DesiredEntry;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.ReconciliationCalculator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one bridge from Forge 66 into the main score. A completed quest should have
 * exactly one {@code FORGE_QUEST} entry; an unmarked one should have none. Going through
 * reconciliation rather than a bare award is what makes unmarking a quest a clean
 * reversal in the ledger (PRODUCT.md invariant 2) instead of a second code path.
 */
@Component
public class ForgeQuestReconciliationCalculator implements ReconciliationCalculator<ReconcileScope.ForgeQuest> {

    public static final String SOURCE_TYPE = "FORGE_QUEST";
    public static final String RULE_CODE = "FORGE_QUEST";

    private final ForgeQuestRepository quests;
    private final ForgeQuestTemplateRepository templates;
    private final ForgeSettingsProvider settings;

    public ForgeQuestReconciliationCalculator(
            ForgeQuestRepository quests, ForgeQuestTemplateRepository templates, ForgeSettingsProvider settings) {
        this.quests = quests;
        this.templates = templates;
        this.settings = settings;
    }

    @Override
    public Class<ReconcileScope.ForgeQuest> scopeType() {
        return ReconcileScope.ForgeQuest.class;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<DesiredEntry> desiredEntries(ReconcileScope.ForgeQuest scope) {
        ForgeQuest quest = quests.findById(scope.questId()).orElse(null);
        if (quest == null || !quest.isDone()) {
            return List.of();
        }
        int amount = settings.current().pointsFor(quest.getXp());
        if (amount <= 0) {
            return List.of();
        }
        String title =
                templates.findByCode(quest.getTemplateCode()).map(ForgeQuestTemplate::getTitle).orElse("Quest");
        return List.of(
                new DesiredEntry(
                        quest.getQuestDate(),
                        PointsCategory.FORGE,
                        RULE_CODE,
                        amount,
                        SOURCE_TYPE,
                        quest.getId(),
                        "Forge 66: " + title));
    }

    @Override
    public Set<UUID> sourceIdsInScope(ReconcileScope.ForgeQuest scope) {
        return Set.of(scope.questId());
    }
}
