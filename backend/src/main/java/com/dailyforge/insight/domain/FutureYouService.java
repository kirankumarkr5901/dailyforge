package com.dailyforge.insight.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.body.domain.BodyMetric;
import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitStreakService;
import com.dailyforge.run.domain.Run;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.workout.domain.Exercise;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.WorkoutSet;
import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * "Future you": on the 1st of each month, where the last two months' pace leads in 90
 * days. The trends (weight per week, estimated strength per month on the main lifts, run
 * pace and weekly distance, habit consistency) are measured here; the AI only puts them
 * into words, adds a reality check where a trend is going the wrong way, and names one
 * lever. Written once per month and kept.
 */
@Service
public class FutureYouService {

    public static final int HORIZON_DAYS = 90;
    private static final int WINDOW_DAYS = 60;

    public record Projection(LocalDate month, String headline, List<String> lines, String reality, String lever) {}

    private final GeminiClient gemini;
    private final WeekReviewService weeks;
    private final BodyMetricService body;
    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;
    private final RunService runs;
    private final HabitService habits;
    private final HabitStreakService streaks;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FutureYouService(
            GeminiClient gemini,
            WeekReviewService weeks,
            BodyMetricService body,
            WorkoutSetRepository sets,
            ExerciseRepository exercises,
            RunService runs,
            HabitService habits,
            HabitStreakService streaks,
            JdbcTemplate jdbc,
            Clock clock) {
        this.gemini = gemini;
        this.weeks = weeks;
        this.body = body;
        this.sets = sets;
        this.exercises = exercises;
        this.runs = runs;
        this.habits = habits;
        this.streaks = streaks;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** This month's projection on its 1st (written then if needed); null on other days or with AI off. */
    public Projection forToday(UUID userId) {
        LocalDate today = weeks.today(userId);
        if (today.getDayOfMonth() != 1) {
            return null;
        }
        Projection stored = stored(userId, today);
        if (stored != null || !gemini.enabled()) {
            return stored;
        }
        String facts = facts(userId, today);
        gemini.countUse(userId, "future-you", 3, "projections");
        String prompt = "You are a motivating but honest coach. From this person's measured trends over the last "
                + WINDOW_DAYS + " days, describe where they'll be in " + HORIZON_DAYS + " days if they keep exactly this "
                + "pace. Use the numbers given; round sensibly; never invent a trend that isn't there.\n"
                + "- headline: one exciting but true sentence about future them.\n"
                + "- lines: 3 to 5 concrete projections (\"Bench about 62 kg for 5\", \"5 km in about 27 min\", "
                + "\"72.4 to 70.9 kg\"). Only for areas with data.\n"
                + "- reality: one sentence on any trend going the wrong way or too fast to be safe (\"\" if none).\n"
                + "- lever: the ONE change this month that would move future them the most.\n\n" + facts;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("headline", string());
        props.put("lines", array(string(), 5));
        props.put("reality", string());
        props.put("lever", string());
        Projection p = parse(today, gemini.generate(prompt, null, object(props)));
        try {
            jdbc.update(
                    "INSERT INTO future_you (user_id, month, headline, lines, reality, lever, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    userId, Date.valueOf(today), p.headline(), String.join("\n", p.lines()), p.reality(), p.lever(),
                    Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException raced) {
            return stored(userId, today);
        }
        return p;
    }

    private Projection stored(UUID userId, LocalDate month) {
        List<Projection> rows = jdbc.query(
                "SELECT headline, lines, reality, lever FROM future_you WHERE user_id = ? AND month = ?",
                (rs, i) -> new Projection(month, rs.getString(1),
                        rs.getString(2).isBlank() ? List.of() : List.of(rs.getString(2).split("\n")), rs.getString(3), rs.getString(4)),
                userId, Date.valueOf(month));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The measured trends, as plain lines for the prompt. */
    String facts(UUID userId, LocalDate today) {
        LocalDate from = today.minusDays(WINDOW_DAYS);
        StringBuilder f = new StringBuilder("MEASURED TRENDS (last " + WINDOW_DAYS + " days)\n");

        List<BodyMetric> weighIns = body.list(userId, from, today).stream()
                .filter(m -> m.getWeightKg() != null)
                .sorted(Comparator.comparing(BodyMetric::getOccurredOn))
                .toList();
        if (weighIns.size() >= 2) {
            double perWeek = slopePerDay(weighIns.stream().map(m -> new double[] {
                        m.getOccurredOn().toEpochDay(), m.getWeightKg().doubleValue()}).toList()) * 7;
            double latest = weighIns.get(weighIns.size() - 1).getWeightKg().doubleValue();
            f.append(String.format(Locale.ROOT, "- Weight: %.1f kg now, changing %+.2f kg per week (%d weigh-ins)%n",
                    latest, perWeek, weighIns.size()));
        } else {
            f.append("- Weight: not enough weigh-ins\n");
        }

        Map<UUID, List<WorkoutSet>> byExercise = sets.findAllForUserBetween(userId, from, today).stream()
                .collect(Collectors.groupingBy(WorkoutSet::getExerciseId));
        Map<UUID, Exercise> lifts = exercises.findAllById(byExercise.keySet()).stream()
                .filter(e -> e.getKind() == ExerciseKind.STRENGTH)
                .collect(Collectors.toMap(Exercise::getId, e -> e));
        byExercise.entrySet().stream()
                .filter(e -> lifts.containsKey(e.getKey()))
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(3)
                .forEach(e -> {
                    List<double[]> points = e.getValue().stream()
                            .map(s -> new double[] {
                                    s.getLoggedAt().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay(), e1rm(s)})
                            .toList();
                    double best = points.stream().mapToDouble(p -> p[1]).max().orElse(0);
                    double perMonth = points.size() >= 4 ? slopePerDay(points) * 30 : 0;
                    f.append(String.format(Locale.ROOT,
                            "- %s: estimated 1-rep max %.1f kg, changing %+.1f kg per month (%d sets)%n",
                            lifts.get(e.getKey()).getName(), best, perMonth, e.getValue().size()));
                });

        List<Run> recent = runs.list(userId, from, today);
        if (!recent.isEmpty()) {
            double km = recent.stream().mapToInt(Run::getDistanceMeters).sum() / 1000.0;
            List<double[]> paces = recent.stream()
                    .filter(r -> r.getDistanceMeters() >= 1000)
                    .map(r -> new double[] {r.getOccurredOn().toEpochDay(), r.getDurationSeconds() / (r.getDistanceMeters() / 1000.0)})
                    .toList();
            double bestPace = paces.stream().mapToDouble(p -> p[1]).min().orElse(0);
            double paceChangePerMonth = paces.size() >= 3 ? slopePerDay(paces) * 30 : 0;
            f.append(String.format(Locale.ROOT,
                    "- Running: %d runs, %.1f km per week, best pace %s per km, pace changing %+.0f s/km per month%n",
                    recent.size(), km / (WINDOW_DAYS / 7.0), minSec(bestPace), paceChangePerMonth));
        } else {
            f.append("- Running: no runs\n");
        }

        for (Habit h : habits.listActive(userId)) {
            var streak = streaks.compute(h, from);
            long done = streak.doneDates().size();
            f.append(String.format(Locale.ROOT, "- Habit \"%s\": done %d of the last %d days, current streak %d, best %d%n",
                    h.getName(), done, ChronoUnit.DAYS.between(from, today) + 1, streak.currentStreak(), streak.bestStreak()));
        }
        return f.toString();
    }

    /** Epley estimate of a one-rep max from one set. */
    static double e1rm(WorkoutSet s) {
        double kg = s.getTotalWeightKg().doubleValue();
        return s.getReps() <= 1 ? kg : kg * (1 + s.getReps() / 30.0);
    }

    /** Least-squares slope of y over x (x in days). */
    static double slopePerDay(List<double[]> points) {
        int n = points.size();
        if (n < 2) {
            return 0;
        }
        double mx = points.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double my = points.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double num = 0;
        double den = 0;
        for (double[] p : points) {
            num += (p[0] - mx) * (p[1] - my);
            den += (p[0] - mx) * (p[0] - mx);
        }
        return den == 0 ? 0 : num / den;
    }

    private static String minSec(double seconds) {
        int s = (int) Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    static Projection parse(LocalDate month, JsonNode a) {
        List<String> lines = new ArrayList<>();
        for (JsonNode l : a.path("lines")) {
            String line = trim(l.asString(""), 200).replace('\n', ' ');
            if (!line.isEmpty() && lines.size() < 5) {
                lines.add(line);
            }
        }
        String headline = trim(a.path("headline").asString(""), 200);
        return new Projection(
                month,
                headline.isEmpty() ? "Here's where this pace takes you." : headline,
                lines,
                trim(a.path("reality").asString(""), 400),
                trim(a.path("lever").asString(""), 300));
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max).strip() : t;
    }
}
