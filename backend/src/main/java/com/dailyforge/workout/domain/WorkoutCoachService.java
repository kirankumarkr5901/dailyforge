package com.dailyforge.workout.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.enumOf;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * The workout coach on the day's workout: a short pre-workout note, plateau and deload
 * alerts, swaps for stalled exercises, and muscles short of their 10–20 weekly sets.
 * The facts (recent sessions, which lifts have stalled, the week's sets per muscle) are
 * worked out here; the AI only turns them into advice. Kept for the rest of the day, so
 * opening the screen again doesn't ask again.
 */
@Service
public class WorkoutCoachService {

    public static final int DAILY_LIMIT_PER_USER = 12;
    /** Weekly sets per muscle below which it counts as short (the app's 10–20 band). */
    private static final int SESSIONS_SHOWN = 5;
    /** This many sessions without a heavier weight or more reps counts as stalled. */
    static final int STALL_SESSIONS = 3;

    public enum AlertType { PLATEAU, DELOAD, SWAP }

    public record Alert(UUID exerciseId, String exercise, AlertType type, String message, String swapTo) {}

    public record Gap(String muscle, int sets, String message) {}

    public record Coaching(String note, List<Alert> alerts, List<Gap> gaps) {}

    /** One past session of an exercise: its heaviest weight and the reps of each set. */
    record Session(LocalDate date, BigDecimal topKg, List<Integer> reps) {}

    private final GeminiClient gemini;
    private final WorkoutPlanService plans;
    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;
    private final WeeklyVolumeService volume;
    private final DeloadService deload;
    private final MuscleTargetService targets;
    private final Map<String, Coaching> today = new ConcurrentHashMap<>();

    public WorkoutCoachService(
            GeminiClient gemini,
            WorkoutPlanService plans,
            WorkoutSetRepository sets,
            ExerciseRepository exercises,
            WeeklyVolumeService volume,
            DeloadService deload,
            MuscleTargetService targets) {
        this.targets = targets;
        this.gemini = gemini;
        this.plans = plans;
        this.sets = sets;
        this.exercises = exercises;
        this.volume = volume;
        this.deload = deload;
    }

    @Transactional(readOnly = true)
    public Coaching coach(UUID userId, UUID planId, int dayIndex, LocalDate date) {
        gemini.requireEnabled();
        plans.requireOwned(planId, userId);
        String key = userId + "|" + planId + "|" + dayIndex + "|" + date;
        today.keySet().removeIf(k -> !k.endsWith("|" + date));
        Coaching cached = today.get(key);
        if (cached != null) {
            return cached;
        }

        List<PlanExercise> planned = plans.exercisesFor(planId).stream()
                .filter(p -> p.getDayIndex() == dayIndex)
                .toList();
        Map<UUID, Exercise> byId = exercises.findAllById(planned.stream().map(PlanExercise::getExerciseId).toList())
                .stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));
        List<Exercise> library = exercises.findAllVisible(userId);

        StringBuilder facts = new StringBuilder();
        Map<String, UUID> idsByName = new LinkedHashMap<>();
        Set<String> todaysMuscles = new HashSet<>();
        for (PlanExercise p : planned) {
            Exercise e = byId.get(p.getExerciseId());
            if (e == null || e.getKind() == ExerciseKind.CARDIO) {
                continue;
            }
            idsByName.put(e.getName().toLowerCase(Locale.ROOT), e.getId());
            e.getMuscleGroups().forEach(m -> todaysMuscles.add(m.trim().toLowerCase(Locale.ROOT)));
            List<Session> recent = recentSessions(userId, e.getId(), date);
            facts.append("\n").append(e.getName()).append(" (").append(String.join(", ", e.getMuscleGroups())).append("; ")
                    .append(e.getEquipment().name().toLowerCase(Locale.ROOT)).append("). Target: ")
                    .append(p.getTargetSets() == null ? "?" : p.getTargetSets()).append(" sets of ")
                    .append(p.getTargetReps() == null ? "?" : p.getTargetReps())
                    .append(p.getTargetRepsMax() != null && !p.getTargetRepsMax().equals(p.getTargetReps()) ? "-" + p.getTargetRepsMax() : "")
                    .append(" reps.\n");
            if (recent.isEmpty()) {
                facts.append("  No sessions logged yet.\n");
            }
            for (Session s : recent) {
                facts.append("  ").append(s.date()).append(": ").append(s.topKg().stripTrailingZeros().toPlainString())
                        .append(" kg, reps ").append(s.reps()).append('\n');
            }
            if (stalled(recent)) {
                facts.append("  STALLED: no heavier weight or extra reps for ").append(STALL_SESSIONS).append(" sessions. Alternatives: ")
                        .append(alternatives(e, library)).append('\n');
            }
        }

        WeeklyVolumeService.WeeklyVolume week = volume.forWeekOf(userId, date);
        Map<String, Integer> setsByMuscle = week.muscles().stream()
                .collect(Collectors.toMap(WeeklyVolumeService.MuscleSets::group, WeeklyVolumeService.MuscleSets::sets));
        // Each muscle's own weekly target, not one 10-20 band for all (owner request).
        Map<String, Integer> minimums = new LinkedHashMap<>();
        targets.current(userId).forEach((m, t) -> minimums.put(m, t.min()));
        Set<String> todaysMain = todaysMuscles.stream().map(MainMuscles::of).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        StringBuilder weekFacts = new StringBuilder();
        for (String muscle : todaysMain) {
            weekFacts.append("- ").append(muscle).append(": ").append(setsByMuscle.getOrDefault(muscle, 0)).append(" sets")
                    .append(minimums.containsKey(muscle) ? " (weekly target " + minimums.get(muscle) + "+)" : "").append('\n');
        }
        week.muscles().stream()
                .filter(m -> !todaysMain.contains(m.group()))
                .forEach(m -> weekFacts.append("- ").append(m.group()).append(": ").append(m.sets()).append(" sets")
                        .append(minimums.containsKey(m.group()) ? " (weekly target " + minimums.get(m.group()) + "+)" : "")
                        .append('\n'));

        gemini.countUse(userId, "workout-coach", DAILY_LIMIT_PER_USER, "workout coaching");
        String prompt = "You are a strength coach writing a short, practical note for someone about to train. "
                + "Use only the facts below. Be specific (weights, reps, sets) and encouraging, never preachy.\n"
                + "- note: 1-2 sentences on what to push for today, based on the last sessions.\n"
                + "- alerts: only for exercises with a real issue. PLATEAU for a STALLED lift (suggest a rep-scheme "
                + "change or a small deload); DELOAD when recent sessions went backwards; SWAP when a STALLED lift would "
                + "do better replaced, with swapTo set to one of its listed alternatives (otherwise swapTo is \"\").\n"
                + "- gaps: muscles under their own weekly target (shown below) this week (Monday-Sunday, today is "
                + date.getDayOfWeek() + "), with how many sets to add and which of today's exercises or one common "
                + "exercise to add them with. Skip muscles already at their target.\n"
                + (deload.isDeload(userId, date) ? "This is a planned deload day: keep the note easy-going.\n" : "")
                + "\nTODAY'S EXERCISES" + facts
                + "\nSETS PER MUSCLE THIS WEEK SO FAR:\n" + weekFacts;

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("exercise", string());
        alert.put("type", enumOf(List.of("PLATEAU", "DELOAD", "SWAP")));
        alert.put("message", string());
        alert.put("swapTo", string());
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("muscle", string());
        gap.put("message", string());
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("note", string());
        schema.put("alerts", array(object(alert), 6));
        schema.put("gaps", array(object(gap), 6));

        Coaching coaching = parse(gemini.generate(prompt, null, object(schema)), idsByName, setsByMuscle, minimums);
        today.put(key, coaching);
        return coaching;
    }

    private List<Session> recentSessions(UUID userId, UUID exerciseId, LocalDate before) {
        Map<LocalDate, List<WorkoutSet>> byDate = new LinkedHashMap<>();
        for (Object[] row : sets.findHistory(userId, exerciseId)) {
            LocalDate on = (LocalDate) row[1];
            if (on.isBefore(before)) {
                byDate.computeIfAbsent(on, d -> new ArrayList<>()).add((WorkoutSet) row[0]);
            }
        }
        List<Session> out = new ArrayList<>();
        for (var entry : byDate.entrySet()) {
            BigDecimal top = entry.getValue().stream().map(WorkoutSet::getTotalWeightKg).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            List<Integer> reps = entry.getValue().stream()
                    .sorted((a, b) -> Integer.compare(a.getSetNumber(), b.getSetNumber()))
                    .filter(s -> s.getTotalWeightKg().compareTo(top) == 0)
                    .map(WorkoutSet::getReps)
                    .toList();
            out.add(new Session(entry.getKey(), top, reps));
            if (out.size() == SESSIONS_SHOWN) {
                break;
            }
        }
        return out;
    }

    /**
     * Newest first: stalled when none of the last {@link #STALL_SESSIONS} sessions beat the
     * one before them on weight, or on best reps at the same weight.
     */
    static boolean stalled(List<Session> newestFirst) {
        if (newestFirst.size() < STALL_SESSIONS + 1) {
            return false;
        }
        for (int i = 0; i < STALL_SESSIONS; i++) {
            Session now = newestFirst.get(i);
            Session before = newestFirst.get(i + 1);
            int cmp = now.topKg().compareTo(before.topKg());
            int bestNow = now.reps().stream().mapToInt(Integer::intValue).max().orElse(0);
            int bestBefore = before.reps().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (cmp > 0 || (cmp == 0 && bestNow > bestBefore)) {
                return false;
            }
        }
        return true;
    }

    private static String alternatives(Exercise e, List<Exercise> library) {
        String primary = e.getMuscleGroups().isEmpty() ? "" : e.getMuscleGroups().get(0).trim().toLowerCase(Locale.ROOT);
        String names = library.stream()
                .filter(x -> !x.getId().equals(e.getId()) && x.getKind() != ExerciseKind.CARDIO)
                .filter(x -> !x.getMuscleGroups().isEmpty() && x.getMuscleGroups().get(0).trim().toLowerCase(Locale.ROOT).equals(primary))
                .limit(8)
                .map(Exercise::getName)
                .collect(Collectors.joining(", "));
        return names.isEmpty() ? "none in their library" : names;
    }

    static Coaching parse(
            JsonNode answer, Map<String, UUID> idsByName, Map<String, Integer> setsByMuscle, Map<String, Integer> minimums) {
        String note = trim(answer.path("note").asString(""), 280);
        List<Alert> alerts = new ArrayList<>();
        for (JsonNode a : answer.path("alerts")) {
            String name = a.path("exercise").asString("").strip();
            UUID id = idsByName.get(name.toLowerCase(Locale.ROOT));
            String message = trim(a.path("message").asString(""), 240);
            if (id == null || message.isEmpty()) {
                continue;
            }
            AlertType type;
            try {
                type = AlertType.valueOf(a.path("type").asString("PLATEAU"));
            } catch (IllegalArgumentException ex) {
                type = AlertType.PLATEAU;
            }
            alerts.add(new Alert(id, name, type, message, trim(a.path("swapTo").asString(""), 60)));
        }
        List<Gap> gaps = new ArrayList<>();
        for (JsonNode g : answer.path("gaps")) {
            String muscle = g.path("muscle").asString("").strip().toLowerCase(Locale.ROOT);
            String message = trim(g.path("message").asString(""), 240);
            int done = setsByMuscle.getOrDefault(muscle, 0);
            Integer min = minimums.get(MainMuscles.of(muscle));
            if (muscle.isEmpty() || message.isEmpty() || min == null || done >= min) {
                continue;
            }
            gaps.add(new Gap(muscle, done, message));
        }
        return new Coaching(note, alerts, gaps);
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max).strip() : t;
    }
}
