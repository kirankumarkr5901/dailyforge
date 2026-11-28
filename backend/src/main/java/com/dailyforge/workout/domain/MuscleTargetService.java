package com.dailyforge.workout.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.enumOf;
import static com.dailyforge.ai.GeminiClient.integer;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutPlanRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Weekly set targets for each of the ten main muscles. The AI sets them from the
 * person's own training (how often they train, what each muscle has been getting, their
 * plan and goal) and re-checks them every 30 days; a target the user edited is theirs
 * and never overwritten. Without AI, sensible defaults stand in.
 */
@Service
public class MuscleTargetService {

    private static final Logger log = LoggerFactory.getLogger(MuscleTargetService.class);
    static final int REFRESH_DAYS = 30;

    public record Target(String muscle, int min, int max, String source) {}

    private final JdbcTemplate jdbc;
    private final GeminiClient gemini;
    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;
    private final WorkoutPlanRepository plans;

    public MuscleTargetService(
            JdbcTemplate jdbc,
            GeminiClient gemini,
            WorkoutSetRepository sets,
            ExerciseRepository exercises,
            WorkoutPlanRepository plans) {
        this.jdbc = jdbc;
        this.gemini = gemini;
        this.sets = sets;
        this.exercises = exercises;
        this.plans = plans;
    }

    /** The stored targets, with defaults for any muscle not stored. Never calls the AI. */
    public Map<String, Target> current(UUID userId) {
        Map<String, Target> out = new LinkedHashMap<>();
        for (String m : MainMuscles.ALL) {
            int[] d = MainMuscles.DEFAULTS.get(m);
            out.put(m, new Target(m, d[0], d[1], "DEFAULT"));
        }
        jdbc.query("SELECT muscle, min_sets, max_sets, source FROM muscle_target WHERE user_id = ?",
                rs -> {
                    if (out.containsKey(rs.getString(1))) {
                        out.put(rs.getString(1), new Target(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4)));
                    }
                }, userId);
        return out;
    }

    /** The targets for showing: set (or re-checked after 30 days) by the AI first when it's on. */
    public Map<String, Target> targets(UUID userId, LocalDate today) {
        if (gemini.enabled() && due(userId)) {
            try {
                refreshWithAi(userId, today);
            } catch (RuntimeException e) {
                log.warn("Muscle targets for {} not refreshed: {}", userId, e.toString());
            }
        }
        return current(userId);
    }

    private boolean due(UUID userId) {
        List<Timestamp> latest = jdbc.queryForList(
                "SELECT MAX(updated_at) FROM muscle_target WHERE user_id = ? AND source <> 'USER'", Timestamp.class, userId);
        Integer stored = jdbc.queryForObject("SELECT COUNT(*) FROM muscle_target WHERE user_id = ?", Integer.class, userId);
        if (stored == null || stored < MainMuscles.ALL.size()) {
            return true;
        }
        Timestamp t = latest.isEmpty() ? null : latest.get(0);
        return t != null && t.toInstant().isBefore(Instant.now().minus(REFRESH_DAYS, ChronoUnit.DAYS));
    }

    /** The user's own range for a muscle: kept until they change it again. */
    public Map<String, Target> set(UUID userId, String muscle, int min, int max) {
        String m = MainMuscles.of(muscle);
        if (!MainMuscles.ALL.contains(m)) {
            throw ApiException.outOfRange("muscle", "Targets are for the ten main muscles.");
        }
        if (min < 1 || min > 40 || max < min || max > 50) {
            throw ApiException.outOfRange("min", "Use a minimum of 1 to 40 sets and a maximum at or above it.");
        }
        upsert(userId, m, min, max, "USER");
        return current(userId);
    }

    void refreshWithAi(UUID userId, LocalDate today) {
        gemini.countUse(userId, "muscle-targets", 3, "target updates");
        LocalDate from = today.minusWeeks(4);
        List<WorkoutSet> recent = sets.findAllForUserBetween(userId, from, today);
        Map<UUID, Exercise> byId = new java.util.HashMap<>();
        exercises.findAllById(recent.stream().map(WorkoutSet::getExerciseId).distinct().toList()).forEach(e -> byId.put(e.getId(), e));
        Map<String, Integer> perMuscle = new LinkedHashMap<>();
        MainMuscles.ALL.forEach(m -> perMuscle.put(m, 0));
        for (WorkoutSet s : recent) {
            Exercise e = byId.get(s.getExerciseId());
            if (e != null && e.getKind() != ExerciseKind.CARDIO && !e.getMuscleGroups().isEmpty()) {
                perMuscle.computeIfPresent(MainMuscles.of(e.getMuscleGroups().get(0)), (k, v) -> v + 1);
            }
        }
        long trainingDays = sets.findTrainingDays(userId, from, today).size();
        String plan = plans.findByUserIdAndActiveTrueAndArchivedAtIsNull(userId)
                .map(p -> p.getName() + " (" + p.getDayCount() + " days)").orElse("none");
        Integer weeksTraining = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sess.occurred_on) FROM workout_session sess WHERE sess.user_id = ?", Integer.class, userId);
        List<String> goal = jdbc.queryForList("SELECT goal FROM body_profile WHERE user_id = ?", String.class, userId);

        StringBuilder facts = new StringBuilder();
        facts.append("Training days in the last 4 weeks: ").append(trainingDays).append('\n');
        facts.append("Days ever trained: ").append(weeksTraining == null ? 0 : weeksTraining).append('\n');
        facts.append("Active plan: ").append(plan).append('\n');
        facts.append("Body goal: ").append(goal.isEmpty() || goal.get(0) == null ? "not set" : goal.get(0)).append('\n');
        facts.append("Average sets per week, last 4 weeks:\n");
        perMuscle.forEach((m, n) -> facts.append("- ").append(m).append(": ").append(Math.round(n / 4.0 * 10) / 10.0).append('\n'));

        String prompt = "You are a strength coach. Set a realistic weekly hard-set target range (min-max) for each main "
                + "muscle for THIS person: enough to progress, not more than they can recover from or fit into their "
                + "training days. Big muscles (chest, back, quads) usually need more than small ones (biceps, calves), "
                + "and indirect work counts for small ones. Beginners need less; someone training 2 days a week can't "
                + "hit what someone training 5 days can. Start from what they do now and raise it gradually. "
                + "min 2-25, max at least min+2 and at most 30. note: one sentence on the reasoning.\n\n" + facts;
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("muscle", enumOf(MainMuscles.ALL));
        target.put("min", integer());
        target.put("max", integer());
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("targets", array(object(target), MainMuscles.ALL.size()));
        props.put("note", string());
        apply(userId, gemini.generate(prompt, null, object(props)));
    }

    void apply(UUID userId, JsonNode answer) {
        Map<String, Target> mine = current(userId);
        for (JsonNode t : answer.path("targets")) {
            String m = MainMuscles.of(t.path("muscle").asString(""));
            if (!MainMuscles.ALL.contains(m) || "USER".equals(mine.get(m).source())) {
                continue;
            }
            int min = Math.max(2, Math.min(25, t.path("min").asInt(0)));
            int max = Math.max(min + 1, Math.min(30, t.path("max").asInt(min + 4)));
            upsert(userId, m, min, max, "AI");
        }
        // Any muscle the AI skipped is stored at its default, so it isn't asked again until the next check.
        current(userId).values().stream()
                .filter(t -> "DEFAULT".equals(t.source()))
                .forEach(t -> upsert(userId, t.muscle(), t.min(), t.max(), "DEFAULT"));
    }

    private void upsert(UUID userId, String muscle, int min, int max, String source) {
        jdbc.update("DELETE FROM muscle_target WHERE user_id = ? AND muscle = ?", userId, muscle);
        jdbc.update("INSERT INTO muscle_target (user_id, muscle, min_sets, max_sets, source, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                userId, muscle, min, max, source, Timestamp.from(Instant.now()));
    }
}
