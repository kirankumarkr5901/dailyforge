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
            String name, UUID foodId, String why, int proteinG, int kcal, int fatG, int carbsG, int fiberG, int sugarG) {}

    private final GeminiClient gemini;
    private final ProteinService protein;

    public ProteinSuggestionService(GeminiClient gemini, ProteinService protein) {
        this.gemini = gemini;
        this.protein = protein;
    }

    public List<Suggestion> suggest(UUID userId, LocalDate date) {
        gemini.requireEnabled();
        gemini.countUse(userId, "protein-tips", DAILY_LIMIT_PER_USER, "food suggestions");
        ProteinService.Day day = protein.day(userId, date);
        JsonNode answer = gemini.generate(prompt(day), null, schema());
        return suggestions(answer, day.foods());
    }

    static String prompt(ProteinService.Day d) {
        StringBuilder p = new StringBuilder();
        p.append("You are a practical nutrition assistant for someone in India. Suggest 2 or 3 things they could eat ")
                .append("next to reach today's protein target without going far over their calorie budget. ")
                .append("Prefer foods from THEIR FOODS list (use the exact name, one serving as listed). ")
                .append("Only if nothing on the list fits, suggest a common food (mostly Indian, easy to get) with a ")
                .append("realistic serving and its nutrition. Give each a one-sentence reason (\"why\"). ")
                .append("Don't repeat something they already ate today unless it's the best fit.\n\n");
        p.append(String.format(Locale.ROOT, "Protein: %d of %d g eaten (%d g to go).%n",
                d.totalG(), d.targetG(), Math.max(0, d.targetG() - d.totalG())));
        p.append(String.format(Locale.ROOT, "Calories: %d of a %d kcal budget (%d left).%n",
                d.totalKcal(), d.calorieBudget(), Math.max(0, d.calorieBudget() - d.totalKcal())));
        p.append(String.format(Locale.ROOT, "Fat: %d of %d g. Carbs: %d of %d g. Fiber: %d of %d g. Sugar: %d, limit %d g.%n",
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
                "- %s: %d g, %d kcal, %d g%n", f.getName(), f.getGrams(), f.getKcal(), f.getFatG())));
        return p.toString();
    }

    static Map<String, Object> schema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", string());
        item.put("fromTheirFoods", Map.of("type", "boolean"));
        item.put("why", string());
        item.put("proteinG", integer());
        item.put("kcal", integer());
        item.put("fatG", integer());
        item.put("carbsG", integer());
        item.put("fiberG", integer());
        item.put("sugarG", integer());
        return object(Map.of("suggestions", array(object(item), 3)));
    }

    /** List foods are matched by name and keep their own numbers; the rest are clamped estimates. */
    static List<Suggestion> suggestions(JsonNode answer, List<ProteinFood> foods) {
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
                int proteinG = MealPhotoService.clamp(s.path("proteinG"), ProteinService.MAX_FOOD_G);
                int kcal = MealPhotoService.clamp(s.path("kcal"), ProteinService.MAX_KCAL);
                int fatG = MealPhotoService.clamp(s.path("fatG"), ProteinService.MAX_FAT_G);
                if (proteinG + kcal + fatG == 0) {
                    continue;
                }
                out.add(new Suggestion(name.length() > 60 ? name.substring(0, 60).strip() : name, null, why, proteinG, kcal,
                        fatG, MealPhotoService.clamp(s.path("carbsG"), 500), MealPhotoService.clamp(s.path("fiberG"), 100),
                        MealPhotoService.clamp(s.path("sugarG"), 300)));
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }
}
