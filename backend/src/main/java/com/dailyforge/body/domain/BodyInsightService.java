package com.dailyforge.body.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.enumOf;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.workout.domain.WeeklyVolumeService;
import com.dailyforge.workout.domain.WorkoutSet;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Body extras on top of weigh-ins: the body profile (which body-fat formula, what the
 * person is aiming for), the US Navy body-fat estimate from tape measurements, and the AI
 * "am I heading the right way?" check, which reads the measured trends against the goal.
 */
@Service
public class BodyInsightService {

    public enum Sex { MALE, FEMALE }

    public enum Goal { LOSE_FAT, BUILD_MUSCLE, RECOMP, MAINTAIN }

    public enum Verdict { ON_TRACK, MIXED, OFF_TRACK, NOT_ENOUGH_DATA }

    public record Profile(Sex sex, Goal goal) {}

    public record FatPoint(LocalDate date, BigDecimal pct) {}

    public record Direction(Verdict verdict, String summary, List<String> points) {}

    private final JdbcTemplate jdbc;
    private final BodyMetricService body;
    private final GeminiClient gemini;
    private final WorkoutSetRepository sets;
    private final WeeklyVolumeService volume;
    private final DayService dayService;
    private final IdentityService identity;
    private final Map<String, Direction> today = new ConcurrentHashMap<>();

    public BodyInsightService(
            JdbcTemplate jdbc,
            BodyMetricService body,
            GeminiClient gemini,
            WorkoutSetRepository sets,
            WeeklyVolumeService volume,
            DayService dayService,
            IdentityService identity) {
        this.jdbc = jdbc;
        this.body = body;
        this.gemini = gemini;
        this.sets = sets;
        this.volume = volume;
        this.dayService = dayService;
        this.identity = identity;
    }

    // ------------------------------------------------------------------ profile

    public Profile profile(UUID userId) {
        List<Profile> rows = jdbc.query("SELECT sex, goal FROM body_profile WHERE user_id = ?",
                (rs, i) -> new Profile(
                        rs.getString(1) == null ? null : Sex.valueOf(rs.getString(1)),
                        rs.getString(2) == null ? null : Goal.valueOf(rs.getString(2))),
                userId);
        return rows.isEmpty() ? new Profile(null, null) : rows.get(0);
    }

    public Profile saveProfile(UUID userId, Sex sex, Goal goal) {
        jdbc.update("DELETE FROM body_profile WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO body_profile (user_id, sex, goal, updated_at) VALUES (?, ?, ?, ?)",
                userId, sex == null ? null : sex.name(), goal == null ? null : goal.name(), Timestamp.from(Instant.now()));
        this.today.keySet().removeIf(k -> k.startsWith(userId + "|"));
        return profile(userId);
    }

    // ------------------------------------------------------------------ body fat

    /** Every day with what the formula needs, oldest first. Empty until the formula is chosen. */
    public List<FatPoint> bodyFat(UUID userId, LocalDate from, LocalDate to) {
        Sex sex = profile(userId).sex();
        if (sex == null) {
            return List.of();
        }
        List<FatPoint> out = new ArrayList<>();
        for (BodyMetric m : body.list(userId, from, to).stream().sorted(Comparator.comparing(BodyMetric::getOccurredOn)).toList()) {
            BigDecimal height = body.resolveHeightCm(userId, m);
            var t = m.getMeasurements();
            BigDecimal pct = navy(sex, height, t.waistCm(), t.neckCm(), t.hipsCm());
            if (pct != null) {
                out.add(new FatPoint(m.getOccurredOn(), pct));
            }
        }
        return out;
    }

    /**
     * US Navy estimate, one decimal; null when a needed measurement is missing or the
     * numbers can't make sense (waist not above neck).
     */
    public static BigDecimal navy(Sex sex, BigDecimal heightCm, BigDecimal waistCm, BigDecimal neckCm, BigDecimal hipsCm) {
        if (sex == null || heightCm == null || waistCm == null || neckCm == null) {
            return null;
        }
        double h = heightCm.doubleValue();
        double w = waistCm.doubleValue();
        double n = neckCm.doubleValue();
        double pct;
        if (sex == Sex.MALE) {
            if (w - n <= 0) {
                return null;
            }
            pct = 495 / (1.0324 - 0.19077 * Math.log10(w - n) + 0.15456 * Math.log10(h)) - 450;
        } else {
            if (hipsCm == null || w + hipsCm.doubleValue() - n <= 0) {
                return null;
            }
            pct = 495 / (1.29579 - 0.35004 * Math.log10(w + hipsCm.doubleValue() - n) + 0.22100 * Math.log10(h)) - 450;
        }
        if (Double.isNaN(pct) || pct < 2 || pct > 70) {
            return null;
        }
        return BigDecimal.valueOf(pct).setScale(1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ direction

    /** "Am I heading the right way?", from the last eight weeks, kept for the day. */
    public Direction direction(UUID userId) {
        LocalDate d = dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        String key = userId + "|" + d;
        Direction cached = today.get(key);
        if (cached != null) {
            return cached;
        }
        Profile profile = profile(userId);
        if (profile.goal() == null) {
            throw ApiException.outOfRange("goal", "Choose what you're aiming for first.");
        }
        LocalDate from = d.minusWeeks(8);
        List<BodyMetric> entries = body.list(userId, from, d).stream()
                .sorted(Comparator.comparing(BodyMetric::getOccurredOn)).toList();
        if (entries.size() < 2) {
            return new Direction(Verdict.NOT_ENOUGH_DATA,
                    "Log your weight (and tape, if you can) at least twice over a couple of weeks, then ask again.", List.of());
        }
        gemini.requireEnabled();
        gemini.countUse(userId, "body-direction", 5, "direction checks");

        StringBuilder f = new StringBuilder();
        f.append("GOAL: ").append(profile.goal()).append(profile.sex() == null ? "" : ", " + profile.sex().name().toLowerCase(Locale.ROOT)).append('\n');
        BodyMetric first = entries.get(0);
        BodyMetric last = entries.get(entries.size() - 1);
        f.append("Period: ").append(first.getOccurredOn()).append(" to ").append(last.getOccurredOn()).append(", ")
                .append(entries.size()).append(" entries\n");
        f.append("Weight: ").append(fmt(first.getWeightKg())).append(" -> ").append(fmt(last.getWeightKg())).append(" kg\n");
        Map<String, Function<BodyMetric.Measurements, BigDecimal>> sites = new LinkedHashMap<>();
        sites.put("Waist", BodyMetric.Measurements::waistCm);
        sites.put("Chest", BodyMetric.Measurements::chestCm);
        sites.put("Arm", BodyMetric.Measurements::armCm);
        sites.put("Hips", BodyMetric.Measurements::hipsCm);
        sites.put("Thigh", BodyMetric.Measurements::thighCm);
        sites.put("Neck", BodyMetric.Measurements::neckCm);
        sites.forEach((name, get) -> {
            List<BigDecimal> values = entries.stream().map(e -> get.apply(e.getMeasurements())).filter(v -> v != null).toList();
            if (values.size() >= 2) {
                f.append(name).append(": ").append(fmt(values.get(0))).append(" -> ").append(fmt(values.get(values.size() - 1))).append(" cm\n");
            }
        });
        List<FatPoint> fat = bodyFat(userId, from, d);
        if (fat.size() >= 2) {
            f.append("Body fat (Navy estimate): ").append(fat.get(0).pct()).append(" -> ").append(fat.get(fat.size() - 1).pct()).append(" %\n");
        }
        List<WorkoutSet> training = sets.findAllForUserBetween(userId, from, d);
        f.append("Training: ").append(training.size()).append(" sets in 8 weeks; this week by muscle: ")
                .append(volume.forWeekOf(userId, d).muscles().stream().map(m -> m.group() + " " + m.sets()).toList()).append('\n');

        String prompt = "You are a sensible body-composition coach. From the measured changes below, say whether this "
                + "person is heading the right way for their goal. Waist down with weight down suggests fat loss; arms or "
                + "chest up with stable waist suggests muscle gain; for RECOMP, waist down while arms/chest hold or rise is "
                + "good even if weight is flat. Be honest about noise (a few entries, small changes). No medical advice.\n"
                + "verdict: ON_TRACK, MIXED or OFF_TRACK. summary: 1-2 sentences. points: up to 4 short, specific "
                + "observations or next steps.\n\n" + f;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("verdict", enumOf(List.of("ON_TRACK", "MIXED", "OFF_TRACK")));
        props.put("summary", string());
        props.put("points", array(string(), 4));
        Direction result = parse(gemini.generate(prompt, null, object(props)));
        today.keySet().removeIf(k -> k.startsWith(userId + "|"));
        today.put(key, result);
        return result;
    }

    static Direction parse(JsonNode a) {
        Verdict v;
        try {
            v = Verdict.valueOf(a.path("verdict").asString("MIXED"));
        } catch (IllegalArgumentException e) {
            v = Verdict.MIXED;
        }
        List<String> points = new ArrayList<>();
        a.path("points").forEach(p -> {
            String t = p.asString("").strip();
            if (!t.isEmpty() && points.size() < 4) {
                points.add(t.length() > 200 ? t.substring(0, 200) : t);
            }
        });
        String summary = a.path("summary").asString("").strip();
        return new Direction(v, summary.length() > 400 ? summary.substring(0, 400) : summary, points);
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "?" : v.stripTrailingZeros().toPlainString();
    }
}
