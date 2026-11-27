package com.dailyforge.forge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Checking what the AI planned before it becomes quests. No network. */
class ForgeAiPlannerTest {

    private static final ForgeSettings SETTINGS =
            new ForgeSettings(66, List.of(3, 4, 5), 3, 100, 1.5, 1.2, 25, 10, Map.of(7, 50), 0.5);

    @Test
    void keepsKnownAreasClampsTiersDropsRepeatsAndCapsTheDaysQuests() {
        var node = JsonMapper.builder().build().readTree("""
                [{"day":1,"quests":[
                   {"title":"Walk 20 minutes","description":"After lunch.","area":"FITNESS","tier":7},
                   {"title":"walk 20 minutes","description":"dup","area":"FITNESS","tier":1},
                   {"title":"Budget review","description":"10 min.","area":"MONEY","tier":1},
                   {"title":"Read 10 pages","description":"","area":"learning","tier":0},
                   {"title":"Lights out 23:00","description":"","area":"HEALTH","tier":1},
                   {"title":"One too many","description":"","area":"FOCUS","tier":1}]},
                 {"day":1,"quests":[{"title":"Second day one","area":"FOCUS","tier":1}]},
                 {"day":9,"quests":[{"title":"Out of range","area":"FOCUS","tier":1}]}]
                """);
        var days = ForgeAiPlanner.days(node, SETTINGS, 1, 7);
        assertThat(days).hasSize(1);
        assertThat(days.get(0).quests()).extracting(ForgeAiPlanner.QuestDraft::title)
                .containsExactly("Walk 20 minutes", "Read 10 pages", "Lights out 23:00");
        assertThat(days.get(0).quests().get(0).tier()).isEqualTo(3);
        assertThat(days.get(0).quests().get(1).tier()).isEqualTo(1);
    }

    @Test
    void xpGrowsWithTierAndHardMode() {
        assertThat(ForgeAiPlanner.xpFor(1, ForgeMode.NORMAL, SETTINGS)).isEqualTo(10);
        assertThat(ForgeAiPlanner.xpFor(3, ForgeMode.NORMAL, SETTINGS)).isEqualTo(30);
        assertThat(ForgeAiPlanner.xpFor(2, ForgeMode.HARD, SETTINGS)).isEqualTo(24);
    }

    @Test
    void emptyAnswersAreDroppedFromTheInterview() {
        var clean = ForgeAiPlanner.clean(java.util.Arrays.asList(
                new ForgeAiPlanner.Turn("Q?", " "), null, new ForgeAiPlanner.Turn("How do you sleep?", "Badly")));
        assertThat(clean).containsExactly(new ForgeAiPlanner.Turn("How do you sleep?", "Badly"));
    }
}
