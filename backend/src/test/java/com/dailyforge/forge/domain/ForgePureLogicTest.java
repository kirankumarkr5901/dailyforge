package com.dailyforge.forge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.forge.domain.ForgeProgressCalculator.DayStatus;
import com.dailyforge.forge.domain.ForgeProgressCalculator.Progress;
import com.dailyforge.forge.domain.ForgeProgressCalculator.QuestFact;
import com.dailyforge.forge.domain.PlanGenerator.PlannedQuest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The parts of Forge 66 that need no database: levels, scoring, plan shape, XP walk. */
class ForgePureLogicTest {

    private static final ForgeSettings SETTINGS =
            new ForgeSettings(66, List.of(3, 4, 5), 3, 100, 1.5, 1.2, 25, 10, Map.of(7, 50, 21, 150, 66, 500), 0.5);

    private static final LocalDate START = LocalDate.of(2026, 3, 1);

    @Nested
    class Levels {

        @Test
        void everyoneStartsAtLevelOneWithNoXp() {
            LevelCurve.Level level = LevelCurve.levelFor(0, 100, 1.5);
            assertThat(level.level()).isEqualTo(1);
            assertThat(level.xpIntoLevel()).isZero();
            assertThat(level.xpForNextLevel()).isEqualTo(100);
        }

        @Test
        void crossingAThresholdExactlyLevelsUp() {
            // 1→2 costs 100, 2→3 costs round(100 * 2^1.5) = 283.
            assertThat(LevelCurve.levelFor(99, 100, 1.5).level()).isEqualTo(1);
            assertThat(LevelCurve.levelFor(100, 100, 1.5).level()).isEqualTo(2);
            assertThat(LevelCurve.levelFor(382, 100, 1.5).level()).isEqualTo(2);
            assertThat(LevelCurve.levelFor(383, 100, 1.5).level()).isEqualTo(3);
            assertThat(LevelCurve.levelFor(150, 100, 1.5).xpIntoLevel()).isEqualTo(50);
        }

        @Test
        void levelNeverGoesDownAsXpRises() {
            int previous = 1;
            for (int xp = 0; xp < 20_000; xp += 37) {
                int level = LevelCurve.levelFor(xp, 100, 1.5).level();
                assertThat(level).isGreaterThanOrEqualTo(previous);
                previous = level;
            }
        }
    }

    @Nested
    class Questionnaire {

        private Map<String, List<String>> calmAnswers() {
            Map<String, List<String>> answers = new HashMap<>();
            for (ForgeQuestionnaire.Question q : ForgeQuestionnaire.QUESTIONS) {
                answers.put(q.code(), List.of(q.options().get(0).code()));
            }
            return answers;
        }

        @Test
        void weakAreasScoreHighestAndBecomeTheFocus() {
            Map<String, List<String>> answers = calmAnswers();
            answers.put("SCREENS", List.of("OVER_3H"));
            answers.put("ATTENTION", List.of("MINUTES"));
            answers.put("MOVEMENT", List.of("RARELY"));
            answers.put("PRIORITY", List.of("FOCUS", "LEARNING"));

            Map<ForgeArea, Integer> scores = ForgeQuestionnaire.score(answers);

            assertThat(scores.get(ForgeArea.FOCUS)).isEqualTo(4 + 3 + 4);
            assertThat(ForgeQuestionnaire.focusAreas(scores, 3))
                    .containsExactly(ForgeArea.FOCUS, ForgeArea.FITNESS, ForgeArea.LEARNING);
        }

        @Test
        void anUnansweredQuestionIsRefused() {
            Map<String, List<String>> answers = calmAnswers();
            answers.remove("PEOPLE");
            assertThatThrownBy(() -> ForgeQuestionnaire.score(answers)).isInstanceOf(ApiException.class);
        }

        @Test
        void tooManyPicksOrAnUnknownOptionIsRefused() {
            Map<String, List<String>> tooMany = calmAnswers();
            tooMany.put("PRIORITY", List.of("FOCUS", "HEALTH", "SOCIAL"));
            assertThatThrownBy(() -> ForgeQuestionnaire.score(tooMany)).isInstanceOf(ApiException.class);

            Map<String, List<String>> unknown = calmAnswers();
            unknown.put("MORNINGS", List.of("NOT_AN_OPTION"));
            assertThatThrownBy(() -> ForgeQuestionnaire.score(unknown)).isInstanceOf(ApiException.class);
        }
    }

    @Nested
    class Plan {

        private final List<ForgeQuestTemplate> catalogue = catalogue();

        private static List<ForgeQuestTemplate> catalogue() {
            List<ForgeQuestTemplate> all = new ArrayList<>();
            for (ForgeArea area : ForgeArea.values()) {
                for (int tier = 1; tier <= 3; tier++) {
                    for (int i = 1; i <= 2; i++) {
                        all.add(ForgeQuestTemplate.of(area + "_" + tier + "_" + i, area, tier, tier * 10));
                    }
                }
            }
            return all;
        }

        private final List<ForgeArea> focus = List.of(ForgeArea.FOCUS, ForgeArea.HEALTH, ForgeArea.SOCIAL);
        private final Map<ForgeArea, Integer> scores = Map.of(ForgeArea.FOCUS, 9, ForgeArea.HEALTH, 5, ForgeArea.SOCIAL, 2);

        private List<PlannedQuest> plan(long seed, ForgeMode mode) {
            return PlanGenerator.generate(seed, START, SETTINGS, mode, focus, scores, catalogue);
        }

        @Test
        void sixtySixDaysWithMoreQuestsEachPhase() {
            Map<Integer, Long> perDay =
                    plan(42, ForgeMode.NORMAL).stream()
                            .collect(Collectors.groupingBy(PlannedQuest::dayNumber, Collectors.counting()));

            assertThat(perDay).hasSize(66);
            assertThat(perDay.get(1)).isEqualTo(3);
            assertThat(perDay.get(22)).isEqualTo(3);
            assertThat(perDay.get(23)).isEqualTo(4);
            assertThat(perDay.get(45)).isEqualTo(5);
            assertThat(perDay.get(66)).isEqualTo(5);
        }

        @Test
        void questsComeOnlyFromFocusAreasAndNeverAboveThePhasesTier() {
            Map<String, ForgeQuestTemplate> byCode =
                    catalogue.stream().collect(Collectors.toMap(ForgeQuestTemplate::getCode, t -> t));
            for (PlannedQuest q : plan(7, ForgeMode.NORMAL)) {
                ForgeQuestTemplate t = byCode.get(q.templateCode());
                assertThat(focus).contains(t.getArea());
                assertThat(t.getTier()).isLessThanOrEqualTo(SETTINGS.phaseOf(q.dayNumber()));
            }
        }

        @Test
        void aDayNeverRepeatsAQuestAndEachPhaseHasOneAnchor() {
            List<PlannedQuest> plan = plan(99, ForgeMode.NORMAL);
            Map<Integer, List<String>> byDay =
                    plan.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            PlannedQuest::dayNumber,
                                            Collectors.mapping(PlannedQuest::templateCode, Collectors.toList())));
            byDay.values().forEach(codes -> assertThat(codes).doesNotHaveDuplicates());

            // The anchor is the first quest of each day and holds for the whole phase,
            // drawn from the area that needed the most work.
            String phaseOneAnchor = byDay.get(1).get(0);
            for (int day = 1; day <= 22; day++) {
                assertThat(byDay.get(day).get(0)).isEqualTo(phaseOneAnchor);
            }
            assertThat(phaseOneAnchor).startsWith("FOCUS_1");
            assertThat(byDay.get(45).get(0)).startsWith("FOCUS_3");
        }

        @Test
        void theSameSeedGivesTheSamePlan() {
            assertThat(plan(1234, ForgeMode.NORMAL)).isEqualTo(plan(1234, ForgeMode.NORMAL));
        }

        @Test
        void hardModePaysMoreXpPerQuest() {
            PlannedQuest normal = plan(5, ForgeMode.NORMAL).get(0);
            PlannedQuest hard = plan(5, ForgeMode.HARD).get(0);
            assertThat(hard.templateCode()).isEqualTo(normal.templateCode());
            assertThat(hard.xp()).isEqualTo((int) Math.round(normal.xp() * 1.2));
        }
    }

    @Nested
    class Progression {

        private ForgeProgram program(ForgeMode mode) {
            return ForgeProgram.start(java.util.UUID.randomUUID(), START, 66, mode, List.of(ForgeArea.FOCUS), "{}");
        }

        /** Two quests a day, 10 XP each, done according to {@code doneByDay}. */
        private List<QuestFact> quests(ForgeProgram p, int days, java.util.function.IntFunction<Integer> doneOnDay) {
            List<QuestFact> facts = new ArrayList<>();
            for (int day = 1; day <= 66; day++) {
                int done = day <= days ? doneOnDay.apply(day) : 0;
                for (int i = 0; i < 2; i++) {
                    facts.add(new QuestFact(p.getId(), START.plusDays(day - 1L), ForgeArea.FOCUS, 10, i < done));
                }
            }
            return facts;
        }

        @Test
        void aFullDayPaysQuestXpPlusTheDayBonusAndStartsAStreak() {
            ForgeProgram p = program(ForgeMode.NORMAL);
            Progress progress =
                    ForgeProgressCalculator.compute(List.of(p), quests(p, 1, d -> 2), START.plusDays(1), SETTINGS);

            assertThat(progress.totalXp()).isEqualTo(20 + 25);
            assertThat(progress.currentStreak()).isEqualTo(1);
            assertThat(progress.stats().get(ForgeArea.FOCUS)).isEqualTo(20);
        }

        @Test
        void sevenStraightDaysPaysTheStreakBonusOnce() {
            ForgeProgram p = program(ForgeMode.NORMAL);
            Progress progress =
                    ForgeProgressCalculator.compute(List.of(p), quests(p, 8, d -> 2), START.plusDays(8), SETTINGS);

            assertThat(progress.totalXp()).isEqualTo(8 * (20 + 25) + 50);
            assertThat(progress.bestStreak()).isEqualTo(8);
        }

        @Test
        void aMissedDayResetsTheStreakButTodayIsNotMissedYet() {
            ForgeProgram p = program(ForgeMode.NORMAL);
            // Days 1-2 full, day 3 half, day 4 is today and untouched.
            Progress progress =
                    ForgeProgressCalculator.compute(
                            List.of(p), quests(p, 4, d -> d <= 2 ? 2 : d == 3 ? 1 : 0), START.plusDays(3), SETTINGS);

            assertThat(progress.currentStreak()).isZero();
            assertThat(progress.bestStreak()).isEqualTo(2);
            List<ForgeProgressCalculator.DayResult> days = progress.days().get(p.getId());
            assertThat(days.get(2).status()).isEqualTo(DayStatus.PARTIAL);
            assertThat(days.get(3).status()).isEqualTo(DayStatus.TODAY);
            assertThat(days.get(4).status()).isEqualTo(DayStatus.UPCOMING);
        }

        @Test
        void hardModeCostsXpPerUndoneQuestOnClosedDaysOnly() {
            ForgeProgram normal = program(ForgeMode.NORMAL);
            ForgeProgram hard = program(ForgeMode.HARD);
            // Day 1 full (45 XP), day 2 one of two (10 XP), day 3 is today with nothing done.
            java.util.function.IntFunction<Integer> pattern = d -> d == 1 ? 2 : d == 2 ? 1 : 0;

            int normalXp =
                    ForgeProgressCalculator.compute(List.of(normal), quests(normal, 3, pattern), START.plusDays(2), SETTINGS)
                            .totalXp();
            int hardXp =
                    ForgeProgressCalculator.compute(List.of(hard), quests(hard, 3, pattern), START.plusDays(2), SETTINGS)
                            .totalXp();

            assertThat(normalXp).isEqualTo(55);
            assertThat(hardXp).isEqualTo(55 - 10);
        }

        @Test
        void hardModePenaltiesNeverTakeXpBelowZero() {
            ForgeProgram p = program(ForgeMode.HARD);
            Progress progress =
                    ForgeProgressCalculator.compute(List.of(p), quests(p, 10, d -> 0), START.plusDays(10), SETTINGS);
            assertThat(progress.totalXp()).isZero();
            assertThat(progress.level().level()).isEqualTo(1);
        }

        @Test
        void computingTwiceGivesTheSameAnswer() {
            ForgeProgram p = program(ForgeMode.HARD);
            List<QuestFact> facts = quests(p, 30, d -> d % 3 == 0 ? 1 : 2);
            assertThat(ForgeProgressCalculator.compute(List.of(p), facts, START.plusDays(30), SETTINGS))
                    .isEqualTo(ForgeProgressCalculator.compute(List.of(p), facts, START.plusDays(30), SETTINGS));
        }
    }
}
