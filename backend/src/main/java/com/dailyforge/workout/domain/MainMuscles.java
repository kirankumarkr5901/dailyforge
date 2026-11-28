package com.dailyforge.workout.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ten main muscles that get weekly set targets, and how the names exercises use map
 * onto them: the catalogue still says "legs" and "arms" in places, and the picker offers
 * finer names (lats, traps, obliques). Anything else (forearms) keeps its own name and
 * has no target.
 */
public final class MainMuscles {

    private MainMuscles() {}

    public static final List<String> ALL =
            List.of("chest", "back", "shoulders", "biceps", "triceps", "quads", "hamstrings", "glutes", "calves", "core");

    private static final Map<String, String> ALIASES = Map.of(
            "lats", "back",
            "traps", "back",
            "obliques", "core",
            "abs", "core",
            "legs", "quads",
            "arms", "biceps",
            "delts", "shoulders");

    /** Sensible starting ranges when the AI can't set them (sets per week, min-max). */
    public static final Map<String, int[]> DEFAULTS = Map.of(
            "chest", new int[] {10, 14},
            "back", new int[] {10, 14},
            "quads", new int[] {10, 14},
            "hamstrings", new int[] {8, 12},
            "shoulders", new int[] {8, 12},
            "glutes", new int[] {8, 12},
            "biceps", new int[] {6, 10},
            "triceps", new int[] {6, 10},
            "calves", new int[] {6, 10},
            "core", new int[] {6, 10});

    /** The main muscle a group name counts toward, or the name itself when it isn't one. */
    public static String of(String group) {
        String g = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(g, g);
    }
}
