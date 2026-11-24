package com.dailyforge.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.insight.domain.DailySummaryService;
import com.dailyforge.insight.domain.WeekReviewService;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.skill.domain.SkillService;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The weekly review's numbers, and a skill block no longer passing as a workout day. */
@SpringBootTest
@ActiveProfiles("test")
class WeekReviewTest {

    @Autowired private WeekReviewService reviews;
    @Autowired private DailySummaryService summaries;
    @Autowired private WorkoutSetService setService;
    @Autowired private ExerciseService exercises;
    @Autowired private ProteinService protein;
    @Autowired private SkillService skills;
    @Autowired private BodyMetricService body;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Test
    void theWeekIsSummedFromWhatWasLogged() {
        UUID user = TestUsers.create(users, settings);
        UUID bench = exercises.create(user, "Bench " + UUID.randomUUID(), ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("chest"), false).getId();
        setService.logSet(user, TODAY, bench, null, null, new BigDecimal("50"), WeightMode.COMBINED, null, 8);
        setService.logSet(user, TODAY, bench, null, null, new BigDecimal("50"), WeightMode.COMBINED, null, 8);
        protein.log(user, TODAY, "Chicken", new ProteinService.Amounts(150, 1200, 30));

        var week = reviews.review(user, TODAY);

        assertThat(week.trainingDays()).isEqualTo(1);
        assertThat(week.sets()).isEqualTo(2);
        assertThat(week.nutritionDaysLogged()).isEqualTo(1);
        assertThat(week.daysWithinCalories()).isEqualTo(1);
        assertThat(week.daysProteinHit()).isEqualTo(1);
        assertThat(week.points()).isPositive();
        assertThat(week.weekEnd()).isEqualTo(week.weekStart().plusDays(6));
    }

    @Test
    void noWeighInThisWeekMeansNoWeightChange() {
        UUID user = TestUsers.create(users, settings);
        body.upsert(user, TODAY.minusDays(14), new BigDecimal("80"), null, null, null);

        assertThat(reviews.review(user, TODAY).weightChangeKg()).isNull();
    }

    @Test
    void aSkillBlockAloneIsNotAWorkoutDay() {
        UUID user = TestUsers.create(users, settings);
        skills.markDone(user, "HANDSTAND", TODAY);

        var day = summaries.heatmap(user, TODAY, TODAY).get(0);

        assertThat(day.hasWorkout()).isFalse();
        assertThat(day.pointsTotal()).isEqualTo(5);
    }
}
