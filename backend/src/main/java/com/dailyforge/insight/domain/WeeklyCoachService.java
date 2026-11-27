package com.dailyforge.insight.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.nutrition.domain.NutritionSettings;
import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import com.dailyforge.workout.domain.WeeklyVolumeService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * The AI coach's weekly review: a short summary of the Monday-to-Sunday week, up to three
 * wins and one thing to focus on next week. Written from the week's own numbers compared
 * with the week before, once the week reaches its Sunday, and kept: Home shows it on
 * Sunday and Monday, and the Sunday reminder announces it.
 */
@Service
public class WeeklyCoachService {

    public record Review(LocalDate weekStart, String summary, List<String> wins, String focus) {}

    private final GeminiClient gemini;
    private final WeekReviewService weeks;
    private final WeeklyVolumeService volume;
    private final NutritionSettingsRepository nutrition;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WeeklyCoachService(
            GeminiClient gemini,
            WeekReviewService weeks,
            WeeklyVolumeService volume,
            NutritionSettingsRepository nutrition,
            JdbcTemplate jdbc,
            Clock clock) {
        this.gemini = gemini;
        this.weeks = weeks;
        this.volume = volume;
        this.nutrition = nutrition;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * The review of the week containing {@code anyDay}: the stored one, or a new one once
     * that week has reached its Sunday. Null when AI is off or the week isn't there yet.
     */
    public Review review(UUID userId, LocalDate anyDay) {
        LocalDate start = WeeklyVolumeService.mondayOf(anyDay);
        Review stored = stored(userId, start);
        if (stored != null) {
            return stored;
        }
        LocalDate today = weeks.today(userId);
        if (!gemini.enabled() || today.isBefore(start.plusDays(6))) {
            return null;
        }
        gemini.countUse(userId, "weekly-coach", 3, "weekly reviews");
        Review review = parse(start, gemini.generate(prompt(userId, start), null, schema()));
        try {
            jdbc.update(
                    "INSERT INTO weekly_coach (user_id, week_start, summary, wins, focus, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    userId, Date.valueOf(start), review.summary(), String.join("\n", review.wins()), review.focus(),
                    Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException raced) {
            return stored(userId, start);
        }
        return review;
    }

    /**
     * For the Sunday reminder: the week's review the first time it's asked on a Sunday,
     * null otherwise (not Sunday, already announced, AI off). Marks it announced.
     */
    public Review forSundayPush(UUID userId, LocalDate today) {
        if (today.getDayOfWeek() != DayOfWeek.SUNDAY || !gemini.enabled()) {
            return null;
        }
        Review review = review(userId, today);
        if (review == null) {
            return null;
        }
        int claimed = jdbc.update(
                "UPDATE weekly_coach SET pushed_at = ? WHERE user_id = ? AND week_start = ? AND pushed_at IS NULL",
                Timestamp.from(Instant.now(clock)), userId, Date.valueOf(review.weekStart()));
        return claimed == 1 ? review : null;
    }

    private Review stored(UUID userId, LocalDate start) {
        List<Review> rows = jdbc.query(
                "SELECT summary, wins, focus FROM weekly_coach WHERE user_id = ? AND week_start = ?",
                (rs, i) -> new Review(start, rs.getString(1), splitWins(rs.getString(2)), rs.getString(3)),
                userId, Date.valueOf(start));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static List<String> splitWins(String wins) {
        return wins == null || wins.isBlank() ? List.of() : List.of(wins.split("\n"));
    }

    private String prompt(UUID userId, LocalDate start) {
        WeekReviewService.WeekReview week = weeks.review(userId, start);
        WeekReviewService.WeekReview before = weeks.review(userId, start.minusDays(7));
        StringBuilder p = new StringBuilder();
        p.append("You are a supportive fitness and habits coach. Review this person's week (Monday to Sunday) from ")
                .append("the numbers below, compared with the week before. Be specific and warm, never preachy, no ")
                .append("medical advice. summary: 2-3 sentences. wins: up to 3 short, concrete wins (empty if none). ")
                .append("focus: ONE specific, doable thing to do better next week.\n\n");
        p.append("THIS WEEK / WEEK BEFORE\n");
        line(p, "Training days", week.trainingDays(), before.trainingDays());
        line(p, "Sets lifted", week.sets(), before.sets());
        line(p, "Runs", week.runs(), before.runs());
        p.append(String.format(Locale.ROOT, "- Km run: %.1f / %.1f%n", week.runKm(), before.runKm()));
        line(p, "Habit check-offs", (int) week.habitTicks(), (int) before.habitTicks());
        line(p, "Days with food logged", week.nutritionDaysLogged(), before.nutritionDaysLogged());
        line(p, "Days within the calorie budget", week.daysWithinCalories(), before.daysWithinCalories());
        line(p, "Days the protein target was hit", week.daysProteinHit(), before.daysProteinHit());
        line(p, "Points earned", week.points(), before.points());
        if (week.weightChangeKg() != null) {
            p.append("- Weight change this week: ").append(week.weightChangeKg().stripTrailingZeros().toPlainString()).append(" kg\n");
        }
        NutritionSettings targets = nutrition.findById(userId).orElse(null);
        if (targets != null) {
            p.append("- Daily protein target: ").append(targets.getProteinTargetG()).append(" g; calorie budget: ")
                    .append(targets.getCalorieBudget()).append(" kcal\n");
        }
        p.append("\nSETS PER MUSCLE THIS WEEK (aim 10-20 each):\n");
        var muscles = volume.forWeekOf(userId, start).muscles();
        if (muscles.isEmpty()) {
            p.append("- none\n");
        }
        muscles.forEach(m -> p.append("- ").append(m.group()).append(": ").append(m.sets()).append('\n'));
        return p.toString();
    }

    private static void line(StringBuilder p, String what, int now, int before) {
        p.append("- ").append(what).append(": ").append(now).append(" / ").append(before).append('\n');
    }

    static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("summary", string());
        props.put("wins", array(string(), 3));
        props.put("focus", string());
        return object(props);
    }

    static Review parse(LocalDate start, JsonNode answer) {
        List<String> wins = new ArrayList<>();
        for (JsonNode w : answer.path("wins")) {
            String win = trim(w.asString(""), 200).replace('\n', ' ');
            if (!win.isEmpty()) {
                wins.add(win);
            }
            if (wins.size() == 3) {
                break;
            }
        }
        String summary = trim(answer.path("summary").asString(""), 600);
        String focus = trim(answer.path("focus").asString(""), 400);
        if (summary.isEmpty()) {
            summary = "Here's your week.";
        }
        return new Review(start, summary, wins, focus);
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max).strip() : t;
    }
}
