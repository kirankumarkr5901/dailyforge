package com.dailyforge.demo;

import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.habit.domain.ScheduleDays;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.PlanExercise;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutPlan;
import com.dailyforge.workout.domain.WorkoutPlanService;
import com.dailyforge.workout.domain.WorkoutSetService;
import com.dailyforge.workout.repo.ExerciseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * "Try the demo": one shared account anyone can open from the sign-in screen without
 * creating their own. It's filled with a believable week (habits, a workout plan with
 * sets, meals and water, weigh-ins, a run) so every screen has something to show, and
 * wiped every night so whatever visitors change doesn't last. The data is all within the
 * app's 7-day edit window, written through the same services the app uses.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final IdentityService identity;
    private final UserRepository users;
    private final DayService dayService;
    private final HabitService habits;
    private final HabitLogService habitLogs;
    private final ExerciseService exerciseService;
    private final ExerciseRepository exercises;
    private final WorkoutPlanService plans;
    private final WorkoutSetService sets;
    private final ProteinService protein;
    private final BodyMetricService body;
    private final RunService runs;

    public DemoService(
            IdentityService identity,
            UserRepository users,
            DayService dayService,
            HabitService habits,
            HabitLogService habitLogs,
            ExerciseService exerciseService,
            ExerciseRepository exercises,
            WorkoutPlanService plans,
            WorkoutSetService sets,
            ProteinService protein,
            BodyMetricService body,
            RunService runs) {
        this.identity = identity;
        this.users = users;
        this.dayService = dayService;
        this.habits = habits;
        this.habitLogs = habitLogs;
        this.exerciseService = exerciseService;
        this.exercises = exercises;
        this.plans = plans;
        this.sets = sets;
        this.protein = protein;
        this.body = body;
        this.runs = runs;
    }

    /** A session on the demo account, creating and filling it first if it isn't there. */
    public IdentityService.Session start(String device) {
        IdentityService.DemoSession demo;
        try {
            demo = identity.demoSession(device);
        } catch (DataIntegrityViolationException raced) {
            // Two first visitors at once: the other one created it.
            demo = identity.demoSession(device);
        }
        if (demo.created()) {
            seed(demo.session().user().getId());
        }
        return demo.session();
    }

    /** Every night (03:00 in India): the demo account goes, and comes back fresh on the next visit. */
    @Scheduled(cron = "0 30 21 * * *", zone = "UTC")
    public void reset() {
        identity.demoUserId().ifPresent(id -> {
            users.deleteById(id);
            log.info("Reset the demo account");
        });
    }

    void seed(UUID userId) {
        LocalDate today = dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        step("habits", () -> seedHabits(userId, today));
        step("workouts", () -> seedWorkouts(userId, today));
        step("nutrition", () -> seedNutrition(userId, today));
        step("body", () -> seedBody(userId, today));
        step("runs", () -> runs.log(userId, today.minusDays(3), 5000, 27 * 60, com.dailyforge.run.domain.RunType.TEMPO, "Tempo run by the lake", 4));
    }

    /** A step that fails leaves the rest of the demo standing. */
    private static void step(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.warn("Demo seeding of {} failed: {}", what, e.toString());
        }
    }

    private void seedHabits(UUID userId, LocalDate today) {
        Habit read = habits.create(userId, "Read 10 pages", "book", 10, HabitType.NORMAL, 0, null, null, ScheduleDays.EVERY_DAY, today);
        Habit meditate = habits.create(userId, "Meditate 10 minutes", "brain", 10, HabitType.NORMAL, 0, null, null, ScheduleDays.EVERY_DAY, today);
        habits.create(userId, "No sugar after 6 pm", "candy-off", 15, HabitType.STRICT, 5, null, null, ScheduleDays.EVERY_DAY, today);
        habitLogs.log(read.getId(), userId, today.minusDays(1));
        habitLogs.log(meditate.getId(), userId, today.minusDays(1));
        habitLogs.log(read.getId(), userId, today);
    }

    private void seedWorkouts(UUID userId, LocalDate today) {
        WorkoutPlan plan = plans.create(userId, "Push Pull Legs", 3);
        plans.relabelDay(plan.getId(), userId, 1, "Push");
        plans.relabelDay(plan.getId(), userId, 2, "Pull");
        plans.relabelDay(plan.getId(), userId, 3, "Legs");
        List<Exercise> library = exercises.findAllVisible(userId);

        Exercise bench = find(userId, library, "bench press", Equipment.BARBELL, "chest");
        Exercise ohp = find(userId, library, "overhead press", Equipment.BARBELL, "shoulders");
        Exercise row = find(userId, library, "row", Equipment.BARBELL, "back");
        Exercise curl = find(userId, library, "curl", Equipment.DUMBBELL, "biceps");
        Exercise squat = find(userId, library, "squat", Equipment.BARBELL, "legs");
        Exercise rdl = find(userId, library, "romanian deadlift", Equipment.BARBELL, "hamstrings");

        add(plan, userId, bench, 1);
        add(plan, userId, ohp, 1);
        add(plan, userId, row, 2);
        add(plan, userId, curl, 2);
        add(plan, userId, squat, 3);
        add(plan, userId, rdl, 3);

        // Push six days ago, pull four days ago, legs two days ago: today is push again.
        logSets(userId, today.minusDays(6), plan, 1, bench, "50", 10, 9, 8);
        logSets(userId, today.minusDays(6), plan, 1, ohp, "30", 10, 9, 8);
        logSets(userId, today.minusDays(4), plan, 2, row, "45", 12, 11, 10);
        logSets(userId, today.minusDays(4), plan, 2, curl, "10", 12, 12, 10);
        logSets(userId, today.minusDays(2), plan, 3, squat, "60", 10, 10, 9);
        logSets(userId, today.minusDays(2), plan, 3, rdl, "55", 12, 10, 10);
    }

    /** The catalogue's exercise when there is one, else a demo one of its own. */
    private Exercise find(UUID userId, List<Exercise> library, String name, Equipment equipment, String muscle) {
        return library.stream()
                .filter(e -> e.getKind() == ExerciseKind.STRENGTH)
                .filter(e -> e.getName().toLowerCase(Locale.ROOT).contains(name))
                .findFirst()
                .orElseGet(() -> exerciseService.create(
                        userId,
                        Character.toUpperCase(name.charAt(0)) + name.substring(1),
                        ExerciseKind.STRENGTH,
                        equipment,
                        List.of(muscle),
                        false));
    }

    private void add(WorkoutPlan plan, UUID userId, Exercise exercise, int day) {
        PlanExercise pe = plans.addExercise(plan.getId(), userId, exercise.getId(), day, 3, 8, null);
        plans.updateExercise(plan.getId(), userId, pe.getId(), 3, 8, 12, null, 90);
    }

    private void logSets(UUID userId, LocalDate date, WorkoutPlan plan, int day, Exercise e, String kg, int... reps) {
        for (int r : reps) {
            sets.logSet(userId, date, e.getId(), plan.getId(), day, new BigDecimal(kg), WeightMode.COMBINED, null, r);
        }
    }

    private void seedNutrition(UUID userId, LocalDate today) {
        for (int back = 2; back >= 1; back--) {
            LocalDate d = today.minusDays(back);
            protein.log(userId, d, "3 idli with sambar", new ProteinService.Amounts(9, 290, 4, 55, 5, 3));
            protein.log(userId, d, "Chicken curry with rice", new ProteinService.Amounts(32, 620, 18, 70, 4, 5));
            protein.log(userId, d, "Paneer bhurji with 2 roti", new ProteinService.Amounts(24, 520, 26, 40, 6, 4));
            protein.log(userId, d, "Whey shake", new ProteinService.Amounts(24, 120, 2, 3, 0, 2));
            protein.addWater(userId, d, 1500);
            protein.addWater(userId, d, 1000);
        }
        protein.log(userId, today, "2 egg omelette with toast", new ProteinService.Amounts(16, 300, 15, 25, 2, 3));
        protein.addWater(userId, today, 1000);
    }

    private void seedBody(UUID userId, LocalDate today) {
        body.upsert(userId, today.minusDays(6), new BigDecimal("72.6"), new BigDecimal("175"), null, null);
        body.upsert(userId, today.minusDays(3), new BigDecimal("72.2"), null, null, null);
        body.upsert(userId, today, new BigDecimal("71.9"), null, null, null);
    }
}
