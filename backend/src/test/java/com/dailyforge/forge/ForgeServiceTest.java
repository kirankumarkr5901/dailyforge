package com.dailyforge.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.forge.domain.ForgeArea;
import com.dailyforge.forge.domain.ForgeMode;
import com.dailyforge.forge.domain.ForgeProgramStatus;
import com.dailyforge.forge.domain.ForgeQuest;
import com.dailyforge.forge.domain.ForgeQuestionnaire;
import com.dailyforge.forge.domain.ForgeService;
import com.dailyforge.forge.repo.ForgeProgramRepository;
import com.dailyforge.forge.repo.ForgeQuestRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Forge 66 against the real schema and the real points engine. */
@SpringBootTest
@Import(ForgeClockTestConfig.class)
@ActiveProfiles("test")
class ForgeServiceTest {

    @Autowired private ForgeService forge;
    @Autowired private ForgeProgramRepository programs;
    @Autowired private ForgeQuestRepository quests;
    @Autowired private PointsEntryRepository ledger;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private ForgeClockTestConfig.MutableClock clock;

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 3, 2);

    private UUID user;

    @BeforeEach
    void setUp() {
        setToday(DAY_ONE);
        user = TestUsers.create(users, settings);
    }

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    private static Map<String, List<String>> answers() {
        Map<String, List<String>> answers = new HashMap<>();
        for (ForgeQuestionnaire.Question q : ForgeQuestionnaire.QUESTIONS) {
            answers.put(q.code(), List.of(q.options().get(q.options().size() - 1).code()));
        }
        answers.put("PRIORITY", List.of("FITNESS"));
        return answers;
    }

    private List<ForgeQuest> questsOn(LocalDate date) {
        UUID programId = programs.findFirstByUserIdAndStatus(user, ForgeProgramStatus.ACTIVE).orElseThrow().getId();
        return quests.findAllByProgramIdAndQuestDate(programId, date);
    }

    private int forgePoints() {
        return ledger.findAll().stream()
                .filter(e -> e.getUserId().equals(user) && e.getCategory() == PointsCategory.FORGE)
                .mapToInt(e -> e.getAmount())
                .sum();
    }

    @Test
    void startingBuildsSixtySixDaysFromTheAnswers() {
        ForgeService.Overview overview = forge.start(user, answers(), ForgeMode.NORMAL);

        assertThat(overview.activeProgram()).isPresent();
        assertThat(overview.activeProgram().get().getFocusAreas()).hasSize(3).contains(ForgeArea.FITNESS);
        assertThat(overview.todaysQuests()).hasSize(3);
        assertThat(overview.progress().days().get(overview.activeProgram().get().getId())).hasSize(66);
        assertThat(overview.progress().totalXp()).isZero();
    }

    @Test
    void onlyOneProgramCanRunAtATime() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        assertThatThrownBy(() -> forge.start(user, answers(), ForgeMode.HARD))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already have");
    }

    @Test
    void completingAQuestPaysPointsAndXpAndUndoingReversesBoth() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        ForgeQuest quest = questsOn(DAY_ONE).get(0);

        ForgeService.QuestChange done = forge.markDone(user, quest.getId());
        int expectedPoints = (int) Math.round(quest.getXp() * 0.5);
        assertThat(done.points().delta()).isEqualTo(expectedPoints);
        assertThat(done.overview().progress().totalXp()).isEqualTo(quest.getXp());
        assertThat(forgePoints()).isEqualTo(expectedPoints);

        // Marking it again is a replay: nothing more is paid.
        assertThat(forge.markDone(user, quest.getId()).points().delta()).isZero();
        assertThat(forgePoints()).isEqualTo(expectedPoints);

        ForgeService.QuestChange undone = forge.markNotDone(user, quest.getId());
        assertThat(undone.points().delta()).isEqualTo(-expectedPoints);
        assertThat(undone.overview().progress().totalXp()).isZero();
        assertThat(forgePoints()).isZero();
    }

    @Test
    void finishingEveryQuestCompletesTheDay() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        List<ForgeQuest> today = questsOn(DAY_ONE);

        ForgeService.QuestChange last = null;
        for (ForgeQuest q : today) {
            last = forge.markDone(user, q.getId());
        }

        int questXp = today.stream().mapToInt(ForgeQuest::getXp).sum();
        assertThat(last.dayCompleted()).isTrue();
        assertThat(last.overview().progress().totalXp()).isEqualTo(questXp + 25);
        assertThat(last.overview().progress().currentStreak()).isEqualTo(1);
    }

    @Test
    void onlyTodaysQuestsCanBeChanged() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        ForgeQuest tomorrow = questsOn(DAY_ONE.plusDays(1)).get(0);
        assertThatThrownBy(() -> forge.markDone(user, tomorrow.getId())).isInstanceOf(ApiException.class);

        ForgeQuest today = questsOn(DAY_ONE).get(0);
        setToday(DAY_ONE.plusDays(1));
        assertThatThrownBy(() -> forge.markDone(user, today.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void anotherUsersQuestIsNotFound() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        ForgeQuest quest = questsOn(DAY_ONE).get(0);
        UUID stranger = TestUsers.create(users, settings);
        assertThatThrownBy(() -> forge.markDone(stranger, quest.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void daysMissedWhileAwayAreSettledOnTheNextReadAndHardModeChargesXpNotPoints() {
        forge.start(user, answers(), ForgeMode.HARD);
        List<ForgeQuest> dayOne = questsOn(DAY_ONE);
        for (ForgeQuest q : dayOne) {
            forge.markDone(user, q.getId());
        }
        int afterDayOne = forge.overview(user).progress().totalXp();
        int pointsAfterDayOne = forgePoints();

        // Three days away: days 2 and 3 close with nothing done, day 4 is today.
        setToday(DAY_ONE.plusDays(3));
        ForgeService.Overview back = forge.overview(user);

        int missedQuests = questsOn(DAY_ONE.plusDays(1)).size() + questsOn(DAY_ONE.plusDays(2)).size();
        assertThat(back.progress().totalXp()).isEqualTo(Math.max(0, afterDayOne - missedQuests * 10));
        assertThat(back.progress().currentStreak()).isZero();
        assertThat(back.progress().bestStreak()).isEqualTo(1);
        assertThat(forgePoints()).isEqualTo(pointsAfterDayOne);

        // Reading again settles nothing new.
        assertThat(forge.overview(user).progress().totalXp()).isEqualTo(back.progress().totalXp());
    }

    @Test
    void aProgramPastItsLastDayIsMarkedCompletedAndANewOneCanStart() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        setToday(DAY_ONE.plusDays(66));

        ForgeService.Overview overview = forge.overview(user);
        assertThat(overview.activeProgram()).isEmpty();
        assertThat(overview.lastFinishedProgram()).get().extracting(p -> p.getStatus()).isEqualTo(ForgeProgramStatus.COMPLETED);

        assertThat(forge.start(user, answers(), ForgeMode.NORMAL).activeProgram()).isPresent();
    }

    @Test
    void abandoningKeepsXpAlreadyEarnedAndFreesTheSlot() {
        forge.start(user, answers(), ForgeMode.NORMAL);
        ForgeQuest quest = questsOn(DAY_ONE).get(0);
        forge.markDone(user, quest.getId());

        ForgeService.Overview after = forge.abandon(user);
        assertThat(after.activeProgram()).isEmpty();
        assertThat(after.progress().totalXp()).isEqualTo(quest.getXp());
        assertThat(forge.start(user, answers(), ForgeMode.NORMAL).activeProgram()).isPresent();
    }
}
