package com.dailyforge.forge.domain;

import com.dailyforge.common.error.ApiException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The questionnaire that shapes a program. Each answer adds weight to the life areas it
 * says need work; the heaviest areas become the program's focus and the source of its
 * quests.
 *
 * It lives in code rather than a table because it is copy and scoring logic together —
 * a question is only meaningful alongside the weights its answers carry, and both are
 * reviewed and changed as one piece. The wording is original to DailyForge.
 */
public final class ForgeQuestionnaire {

    private ForgeQuestionnaire() {}

    public record Option(String code, String label, Map<ForgeArea, Integer> weights) {
        public Option {
            weights = weights.isEmpty() ? Map.of() : Map.copyOf(weights);
        }
    }

    /** {@code maxPicks} is 1 for an ordinary question; more for "choose up to N". */
    public record Question(String code, String prompt, String hint, int maxPicks, List<Option> options) {
        public Question {
            options = List.copyOf(options);
        }
    }

    private static Option opt(String code, String label) {
        return new Option(code, label, Map.of());
    }

    private static Option opt(String code, String label, ForgeArea area, int weight) {
        return new Option(code, label, Map.of(area, weight));
    }

    private static Option opt(String code, String label, ForgeArea a, int wa, ForgeArea b, int wb) {
        return new Option(code, label, Map.of(a, wa, b, wb));
    }

    public static final List<Question> QUESTIONS =
            List.of(
                    new Question(
                            "MORNINGS",
                            "How do you feel most mornings?",
                            null,
                            1,
                            List.of(
                                    opt("RESTED", "Rested and ready to go"),
                                    opt("SLOW", "Slow until the first coffee", ForgeArea.HEALTH, 1),
                                    opt("DRAINED", "Drained — I rarely sleep well", ForgeArea.HEALTH, 3, ForgeArea.MINDSET, 1))),
                    new Question(
                            "MOVEMENT",
                            "How often do you exercise in a normal week?",
                            null,
                            1,
                            List.of(
                                    opt("OFTEN", "Three or more times"),
                                    opt("SOMETIMES", "Once or twice", ForgeArea.FITNESS, 2),
                                    opt("RARELY", "Rarely or never", ForgeArea.FITNESS, 4))),
                    new Question(
                            "SCREENS",
                            "Outside work, how long are you on your phone each day?",
                            null,
                            1,
                            List.of(
                                    opt("UNDER_1H", "Under an hour"),
                                    opt("ONE_TO_3H", "One to three hours", ForgeArea.FOCUS, 2),
                                    opt("OVER_3H", "More than three hours", ForgeArea.FOCUS, 4))),
                    new Question(
                            "ATTENTION",
                            "When you sit down to work or study, how long can you stay on one thing?",
                            null,
                            1,
                            List.of(
                                    opt("HOUR_PLUS", "An hour or more"),
                                    opt("HALF_HOUR", "About twenty or thirty minutes", ForgeArea.FOCUS, 2),
                                    opt("MINUTES", "A few minutes before I drift", ForgeArea.FOCUS, 3, ForgeArea.MINDSET, 1))),
                    new Question(
                            "FUEL",
                            "How would you describe what you eat and drink?",
                            null,
                            1,
                            List.of(
                                    opt("GOOD", "Mostly good, plenty of water"),
                                    opt("MIXED", "Hit and miss", ForgeArea.HEALTH, 2),
                                    opt("POOR", "Mostly convenience food, not much water", ForgeArea.HEALTH, 4))),
                    new Question(
                            "STRESS",
                            "When things get stressful, what usually happens?",
                            null,
                            1,
                            List.of(
                                    opt("COPE", "I have ways of handling it that work"),
                                    opt("BOTTLE", "I push it down and carry on", ForgeArea.MINDSET, 2),
                                    opt("OVERWHELMED", "It takes over my day", ForgeArea.MINDSET, 4))),
                    new Question(
                            "LEARNING",
                            "When did you last learn something new on purpose — a book, a course, a skill?",
                            null,
                            1,
                            List.of(
                                    opt("THIS_WEEK", "This week"),
                                    opt("THIS_MONTH", "Sometime this month", ForgeArea.LEARNING, 2),
                                    opt("CANT_REMEMBER", "I honestly can't remember", ForgeArea.LEARNING, 4))),
                    new Question(
                            "PEOPLE",
                            "How connected do you feel to the people in your life?",
                            null,
                            1,
                            List.of(
                                    opt("CLOSE", "Close — I see and talk to them often"),
                                    opt("DRIFTING", "Somewhat, but we're drifting", ForgeArea.SOCIAL, 2),
                                    opt("ISOLATED", "Pretty isolated lately", ForgeArea.SOCIAL, 4))),
                    new Question(
                            "PRIORITY",
                            "Which areas matter most to you right now?",
                            "Choose up to two.",
                            2,
                            List.of(
                                    opt("FITNESS", "Getting fitter and stronger", ForgeArea.FITNESS, 4),
                                    opt("FOCUS", "Focus and getting real work done", ForgeArea.FOCUS, 4),
                                    opt("HEALTH", "Sleep, food and energy", ForgeArea.HEALTH, 4),
                                    opt("MINDSET", "A calmer, steadier mind", ForgeArea.MINDSET, 4),
                                    opt("LEARNING", "Learning and growing", ForgeArea.LEARNING, 4),
                                    opt("SOCIAL", "Friends, family and connection", ForgeArea.SOCIAL, 4))));

    /**
     * Totals each area's weight across the answers. Every question must be answered,
     * with between one and {@code maxPicks} known options — anything else is refused
     * rather than quietly scored as zero.
     */
    public static Map<ForgeArea, Integer> score(Map<String, List<String>> answers) {
        Map<ForgeArea, Integer> scores = new EnumMap<>(ForgeArea.class);
        for (ForgeArea area : ForgeArea.values()) {
            scores.put(area, 0);
        }
        if (answers == null) {
            throw ApiException.outOfRange("answers", "Answer every question to build your plan.");
        }

        for (Question question : QUESTIONS) {
            List<String> picked = answers.get(question.code());
            if (picked == null || picked.isEmpty()) {
                throw ApiException.outOfRange("answers", "Answer every question to build your plan.");
            }
            Set<String> unique = new HashSet<>(picked);
            if (unique.size() != picked.size() || picked.size() > question.maxPicks()) {
                throw ApiException.outOfRange("answers", "One of those answers is not valid. Check it and try again.");
            }
            for (String code : picked) {
                Option option =
                        question.options().stream()
                                .filter(o -> o.code().equals(code))
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                ApiException.outOfRange(
                                                        "answers", "One of those answers is not valid. Check it and try again."));
                option.weights().forEach((area, weight) -> scores.merge(area, weight, Integer::sum));
            }
        }
        return scores;
    }

    /** The {@code count} heaviest areas, strongest need first; ties keep the enum's order. */
    public static List<ForgeArea> focusAreas(Map<ForgeArea, Integer> scores, int count) {
        List<ForgeArea> ranked = new ArrayList<>(List.of(ForgeArea.values()));
        ranked.sort(Comparator.comparingInt((ForgeArea a) -> scores.getOrDefault(a, 0)).reversed());
        return List.copyOf(ranked.subList(0, Math.min(count, ranked.size())));
    }
}
