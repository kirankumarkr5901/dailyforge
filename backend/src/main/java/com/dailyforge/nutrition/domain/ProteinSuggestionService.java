package com.dailyforge.nutrition.domain;

import static com.dailyforge.ai.GeminiClient.array;
import static com.dailyforge.ai.GeminiClient.integer;
import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * "What should I eat to hit protein?": two or three options that fit what's left of the
 * day, from the user's own foods first (one tap to log, with the food's own numbers) and
 * a common food or two when the list has nothing that fits. Sends the day's totals and
 * targets, what was eaten, and the food list; nothing about who the user is.
 */
@Service
public class ProteinSuggestionService {

    public static final int DAILY_LIMIT_PER_USER = 15;
    private static final int MAX_FOODS_SENT = 80;

    /**
     * One option. {@code foodId} is set when it's one of the user's foods, and then the
     * numbers are the food's own, not the AI's.
     */
    public record Suggestion(
            String name, UUID foodId, String why, double proteinG, int kcal, double fatG, double carbsG, double fiberG, double sugarG) {}

    private final GeminiClient gemini;
    private final ProteinService protein;

    public ProteinSuggestionService(GeminiClient gemini, ProteinService protein) {
        this.gemini = gemini;
        this.protein = protein;
    }

    public List<Suggestion> suggest(UUID userId, LocalDate date) {
        return suggest(userId, date, List.of());
    }

    /** {@code exclude}: what was already suggested, so "Other ideas" brings new ones. */
    public List<Suggestion> suggest(UUID userId, LocalDate date, List<String> exclude) {
        gemini.requireEnabled();
        gemini.countUse(userId, "protein-tips", DAILY_LIMIT_PER_USER, "food suggestions");
        ProteinService.Day day = protein.day(userId, date);
        JsonNode answer = gemini.generate(prompt(day) + avoid(exclude), null, schema());
        return suggestions(answer, day.foods());
    }

    /** "Fill my day": one meal of 1-4 items that closes today's gaps together. */
    public record MealIdea(String title, String why, List<Suggestion> items) {}

    public MealIdea fillDay(UUID userId, LocalDate date) {
        return fillDay(userId, date, List.of());
    }

    /** {@code exclude}: meals and items already suggested, so "Another idea" is a different one. */
    public MealIdea fillDay(UUID userId, LocalDate date, List<String> exclude) {
        gemini.requireEnabled();
        gemini.countUse(userId, "fill-day", DAILY_LIMIT_PER_USER, "meal ideas");
        ProteinService.Day day = protein.day(userId, date);
        String prompt = "You are a practical nutrition assistant for someone in India. Suggest ONE meal (1 to 4 items "
                + "eaten together) that closes as much as possible of today's remaining gaps at once: protein to its "
                + "target, fiber to its target, carbs and fat toward their targets, while staying within the calorie "
                + "budget and the sugar limit. Prefer items from THEIR FOODS (exact name, one serving as listed); use a "
                + "common food with a realistic serving only where the list has nothing that fits. title: a short name "
                + "for the meal (\"Paneer wrap with sprouts salad\"). why: one sentence on which gaps it closes.\n\n"
                + facts(day) + avoid(exclude);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", string());
        props.put("why", string());
        props.put("suggestions", array(object(itemSchema()), 4));
        JsonNode answer = gemini.generate(prompt, null, object(props));
        String title = answer.path("title").asString("").strip();
        String why = answer.path("why").asString("").strip();
        return new MealIdea(
                title.isEmpty() ? "A meal for the rest of today" : (title.length() > 80 ? title.substring(0, 80) : title),
                why.length() > 200 ? why.substring(0, 200) : why,
                suggestions(answer, day.foods(), 4));
    }

    /** The "already suggested" list, so asking again gives something different (owner-reported repeats). */
    static String avoid(List<String> exclude) {
        List<String> names = exclude == null ? List.of() : exclude.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.strip().length() > 80 ? n.strip().substring(0, 80) : n.strip())
                .distinct().limit(20).toList();
        return names.isEmpty() ? "" : "\nALREADY SUGGESTED (the user wants different ideas; don't repeat these or close "
                + "variations): " + String.join("; ", names) + "\n";
    }

    static String prompt(ProteinService.Day d) {
        StringBuilder p = new StringBuilder();
        p.append("You are a practical nutrition assistant for someone in India. Suggest 2 or 3 things they could eat ")
                .append("next to reach today's protein target without going far over their calorie budget. ")
                .append("Prefer foods from THEIR FOODS list (use the exact name, one serving as listed). ")
                .append("Only if nothing on the list fits, suggest a common food (mostly Indian, easy to get) with a ")
                .append("realistic serving and its nutrition. Give each a one-sentence reason (\"why\"). ")
                .append("Don't repeat something they already ate today unless it's the best fit.\n\n");
        p.append(facts(d));
        return p.toString();
    }

    /** Today's totals against targets, what was eaten, and the food list. */
    static String facts(ProteinService.Day d) {
        StringBuilder p = new StringBuilder();
        p.append(String.format(Locale.ROOT, "Protein: %.1f of %d g eaten (%.1f g to go).%n",
                d.totalG(), d.targetG(), Math.max(0, d.targetG() - d.totalG())));
        p.append(String.format(Locale.ROOT, "Calories: %d of a %d kcal budget (%d left).%n",
                d.totalKcal(), d.calorieBudget(), Math.max(0, d.calorieBudget() - d.totalKcal())));
        p.append(String.format(Locale.ROOT, "Fat: %.1f of %d g. Carbs: %.1f of %d g. Fiber: %.1f of %d g. Sugar: %.1f, limit %d g.%n",
                d.totalFatG(), d.fatTargetG(), d.totalCarbsG(), d.carbsTargetG(), d.totalFiberG(), d.fiberTargetG(),
                d.totalSugarG(), d.sugarLimitG()));
        p.append("\nEATEN TODAY:\n");
        if (d.logs().isEmpty()) {
            p.append("- nothing yet\n");
        }
        for (ProteinLog l : d.logs()) {
            p.append("- ").append(l.getFoodName()).append('\n');
        }
        p.append("\nTHEIR FOODS (name: protein g, kcal, fat g per serving):\n");
        d.foods().stream().limit(MAX_FOODS_SENT).forEach(f -> p.append(String.format(Locale.ROOT,
                "- %s: %.1f g, %d kcal, %.1f g%n", f.getName(), f.getGrams(), f.getKcal(), f.getFatG())));
        return p.toString();
    }

    static Map<String, Object> schema() {
        return object(Map.of("suggestions", array(object(itemSchema()), 3)));
    }

    private static Map<String, Object> itemSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", string());
        item.put("fromTheirFoods", Map.of("type", "boolean"));
        item.put("why", string());
        item.put("proteinG", Map.of("type", "number"));
        item.put("kcal", integer());
        item.put("fatG", Map.of("type", "number"));
        item.put("carbsG", Map.of("type", "number"));
        item.put("fiberG", Map.of("type", "number"));
        item.put("sugarG", Map.of("type", "number"));
        return item;
    }

    /** List foods are matched by name and keep their own numbers; the rest are clamped estimates. */
    static List<Suggestion> suggestions(JsonNode answer, List<ProteinFood> foods) {
        return suggestions(answer, foods, 3);
    }

    static List<Suggestion> suggestions(JsonNode answer, List<ProteinFood> foods, int max) {
        Map<String, ProteinFood> byName = foods.stream()
                .collect(Collectors.toMap(f -> f.getName().trim().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<Suggestion> out = new ArrayList<>();
        for (JsonNode s : answer.path("suggestions")) {
            String name = s.path("name").asString("").strip();
            if (name.isEmpty()) {
                continue;
            }
            String why = s.path("why").asString("").strip();
            why = why.length() > 160 ? why.substring(0, 160).strip() : why;
            ProteinFood food = byName.get(name.toLowerCase(Locale.ROOT));
            if (food != null) {
                out.add(new Suggestion(food.getName(), food.getId(), why, food.getGrams(), food.getKcal(), food.getFatG(),
                        food.getCarbsG(), food.getFiberG(), food.getSugarG()));
            } else {
                double proteinG = MealPhotoService.clamp1(s.path("proteinG"), ProteinService.MAX_FOOD_G);
                int kcal = MealPhotoService.clamp(s.path("kcal"), ProteinService.MAX_KCAL);
                double fatG = MealPhotoService.clamp1(s.path("fatG"), ProteinService.MAX_FAT_G);
                if (proteinG + kcal + fatG == 0) {
                    continue;
                }
                out.add(new Suggestion(name.length() > 60 ? name.substring(0, 60).strip() : name, null, why, proteinG, kcal,
                        fatG, MealPhotoService.clamp1(s.path("carbsG"), 500), MealPhotoService.clamp1(s.path("fiberG"), 100),
                        MealPhotoService.clamp1(s.path("sugarG"), 300)));
            }
            if (out.size() == max) {
                break;
            }
        }
        return out;
    }
}
