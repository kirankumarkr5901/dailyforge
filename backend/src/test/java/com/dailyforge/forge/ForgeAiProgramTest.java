package com.dailyforge.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.dailyforge.forge.domain.ForgeAiPlanner;
import com.dailyforge.forge.domain.ForgeArea;
import com.dailyforge.forge.domain.ForgeMode;
import com.dailyforge.forge.domain.ForgeQuest;
import com.dailyforge.forge.domain.ForgeService;
import com.dailyforge.forge.repo.ForgeQuestRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** An AI-planned Forge 66 run, with the AI itself replaced: no network. */
@SpringBootTest
@Import(ForgeClockTestConfig.class)
@ActiveProfiles("test")
class ForgeAiProgramTest {

    @Autowired private ForgeService forge;
    @Autowired private ForgeQuestRepository quests;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private ForgeClockTestConfig.MutableClock clock;
    @MockitoBean private ForgeAiPlanner ai;

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 3, 2);
    private static final List<ForgeAiPlanner.Turn> TURNS =
            List.of(new ForgeAiPlanner.Turn("What do you want to change?", "Sleep and get fitter"));

    private UUID user;

    @BeforeEach
    void setUp() {
        setToday(DAY_ONE);
        user = TestUsers.create(users, settings);
    }

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private static List<ForgeAiPlanner.DayPlan> week() {
        List<ForgeAiPlanner.DayPlan> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(new ForgeAiPlanner.DayPlan(i, List.of(
                    new ForgeAiPlanner.QuestDraft("Lights out by 23:00", "Phone outside the bedroom.", ForgeArea.HEALTH, 1),
                    new ForgeAiPlanner.QuestDraft("Walk 20 minutes after lunch", "Any pace.", ForgeArea.FITNESS, 2))));
        }
        return days;
    }

    private void planFirstWeek() {
        when(ai.buildPlan(any(), anyList(), any(), any())).thenReturn(new ForgeAiPlanner.Plan(
                "Works 9-6, wants better sleep.", List.of(ForgeArea.HEALTH, ForgeArea.FITNESS), "Sleep\nMove\nBoth", week()));
    }

    @Test
    void anAiProgramPlansItsFirstWeekWithItsOwnQuestsAndTheyPay() {
        planFirstWeek();
        var overview = forge.startAi(user, TURNS, ForgeMode.NORMAL);

        List<ForgeQuest> today = overview.todaysQuests();
        assertThat(today).extracting(ForgeQuest::getTitle)
                .containsExactlyInAnyOrder("Lights out by 23:00", "Walk 20 minutes after lunch");
        assertThat(today).allMatch(ForgeQuest::isCustom);
        assertThat(today).extracting(ForgeQuest::getXp).containsExactlyInAnyOrder(10, 20);
        assertThat(overview.activeProgram().orElseThrow().getPlannedThrough()).isEqualTo(DAY_ONE.plusDays(6));

        ForgeQuest walk = today.stream().filter(q -> q.getTier() == 2).findFirst().orElseThrow();
        assertThat(forge.markDone(user, walk.getId()).points().delta()).isPositive();
    }

    @Test
    void whenTheAiCannotWriteTheNextWeekLastWeeksQuestsRepeat() {
        planFirstWeek();
        forge.startAi(user, TURNS, ForgeMode.NORMAL);
        when(ai.nextWeek(any(), any(), anyInt(), anyInt(), anyList(), any())).thenThrow(new IllegalStateException("busy"));

        setToday(DAY_ONE.plusDays(6)); // day 7: day 8 must be planned before it arrives
        forge.overview(user);

        List<ForgeQuest> dayEight = quests.findAllByUserIdOrderByQuestDateAsc(user).stream()
                .filter(q -> q.getQuestDate().equals(DAY_ONE.plusDays(7)))
                .toList();
        assertThat(dayEight).extracting(ForgeQuest::getTitle)
                .containsExactlyInAnyOrder("Lights out by 23:00", "Walk 20 minutes after lunch");
        assertThat(dayEight).extracting(ForgeQuest::getDayNumber).containsOnly(8);
    }

    /** The week's boss is beaten by finishing every quest in its area, and pays 200 XP once. */
    @Test
    void finishingEveryQuestInTheBossAreaThisWeekBeatsIt() {
        planFirstWeek();
        var overview = forge.startAi(user, TURNS, ForgeMode.NORMAL); // DAY_ONE is a Monday
        var boss = overview.boss().orElseThrow().boss();
        assertThat(boss.level()).isEqualTo(1);
        assertThat(boss.rewardXp()).isEqualTo(200);

        int xpBefore = 0;
        for (int d = 0; d < 7; d++) {
            setToday(DAY_ONE.plusDays(d));
            var today = forge.overview(user);
            if (d == 0) {
                xpBefore = today.progress().totalXp();
            }
            for (ForgeQuest q : today.todaysQuests()) {
                if (q.getArea() == boss.area()) {
                    forge.markDone(user, q.getId());
                }
            }
        }
        var after = forge.overview(user);
        assertThat(after.boss().orElseThrow().boss().status()).isEqualTo(com.dailyforge.forge.domain.ForgeBossService.Status.DEFEATED);
        int questXp = boss.area() == ForgeArea.HEALTH ? 7 * 10 : 7 * 20;
        assertThat(after.progress().totalXp() - xpBefore).isGreaterThanOrEqualTo(questXp + 200);
    }
}
