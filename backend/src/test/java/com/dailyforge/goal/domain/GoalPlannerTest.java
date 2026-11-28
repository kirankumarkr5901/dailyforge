package com.dailyforge.goal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.goal.domain.GoalKind;
import com.dailyforge.goal.domain.GoalPlannerService;
import com.dailyforge.goal.repo.GoalRepository;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.skill.domain.SkillService;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** The goal planner: checking a draft, and accepting it. No network. */
@SpringBootTest
@ActiveProfiles("test")
class GoalPlannerTest {

    @Autowired private GoalPlannerService planner;
    @Autowired private GoalRepository goals;
    @Autowired private HabitService habits;
    @Autowired private SkillService skills;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void aDraftKeepsDatesInsideTheGoalAndDropsWhatItCannotUse() {
        LocalDate today = LocalDate.of(2026, 12, 1);
        LocalDate target = LocalDate.of(2027, 3, 31);
        var a = JsonMapper.builder().build().readTree("""
                {"summary":"Doable.","goal":{"title":"Run 10 km","description":"","kind":"RUN_DISTANCE","targetValue":10,"exerciseName":""},
                 "milestones":[{"title":"Run 5 km","dueDate":"2027-01-15"},{"title":"Too late","dueDate":"2028-01-01"},
                               {"title":"","dueDate":"2027-02-01"},{"title":"Bad date","dueDate":"soon"}],
                 "habits":[{"name":"Easy run","daysPerWeek":9}],
                 "needsSkill":false,"skill":{"name":"","when":"","safety":"","levels":[]},
                 "workoutAdds":[{"exerciseName":"Nope","planDay":1,"sets":3,"repsMin":8,"repsMax":12}]}
                """);
        var d = GoalPlannerService.parse("Run 10 km by March", a, today, target, Map.of(), null, Map.of());
        assertThat(d.goal().kind()).isEqualTo(GoalKind.RUN_DISTANCE);
        assertThat(d.milestones()).extracting(GoalPlannerService.DraftMilestone::dueDate)
                .containsExactly(LocalDate.of(2027, 1, 15), target);
        assertThat(d.habits().get(0).daysPerWeek()).isEqualTo(7);
        assertThat(d.skill()).isNull();
        assertThat(d.workoutAdds()).isEmpty();
    }

    @Test
    void acceptingCreatesTheGoalMilestonesHabitsAndSkill() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var draft = new GoalPlannerService.Draft(
                "Learn to do a pull-up",
                "Build up from hangs.",
                new GoalPlannerService.DraftGoal("First pull-up", "", GoalKind.CUSTOM, null, null, today.plusDays(60)),
                List.of(new GoalPlannerService.DraftMilestone("Hang 30 s", today.plusDays(14)),
                        new GoalPlannerService.DraftMilestone("5 negatives", today.plusDays(30))),
                List.of(new GoalPlannerService.DraftHabit("Dead hang", 3)),
                new GoalPlannerService.DraftSkill("Pull-up", "Pull days", "", List.of(
                        new GoalPlannerService.DraftLevel("Hangs", List.of("Dead hang · 3 x 20 s"), "Hang 30 s"),
                        new GoalPlannerService.DraftLevel("Negatives", List.of("Negative · 3 x 3"), "5 slow negatives"),
                        new GoalPlannerService.DraftLevel("Full", List.of("Pull-up · as many as you can"), "One clean pull-up"))),
                List.of());
        int habitsBefore = habits.listActive(user).size();
        UUID goalId = planner.accept(user, draft);

        assertThat(goals.findById(goalId)).isPresent();
        assertThat(habits.listActive(user)).hasSize(habitsBefore + 1);
        assertThat(skills.board(user, today).skills()).anyMatch(s -> s.custom() && s.skill().name().equals("Pull-up"));
        var plans = planner.plans(user);
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).milestones()).hasSize(2);
        assertThat(plans.get(0).review()).isNull(); // started this week: no review yet

        var toggled = planner.toggleMilestone(user, plans.get(0).milestones().get(0).id());
        assertThat(toggled.get(0).milestones().get(0).doneOn()).isNotNull();
    }

    @Test
    void scheduleSpreadsTheDays() {
        assertThat(GoalPlannerService.schedule(7)).isEqualTo(com.dailyforge.habit.domain.ScheduleDays.EVERY_DAY);
        assertThat(Integer.bitCount(GoalPlannerService.schedule(3))).isEqualTo(3);
        assertThat(Integer.bitCount(GoalPlannerService.schedule(0))).isEqualTo(1);
    }
}
