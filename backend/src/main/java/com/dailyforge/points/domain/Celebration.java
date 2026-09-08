package com.dailyforge.points.domain;

import java.util.Map;

/**
 * One celebration descriptor. {@code details} carries whatever the specific animation
 * needs — a streak length, a milestone distance — without the engine needing a distinct
 * record type per celebration kind.
 */
public record Celebration(CelebrationType type, Map<String, Object> details) {

    public Celebration {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static Celebration of(CelebrationType type) {
        return new Celebration(type, Map.of());
    }

    public static Celebration of(CelebrationType type, Map<String, Object> details) {
        return new Celebration(type, details);
    }

    /**
     * Maps a rule code to the celebration it implies, or none.
     *
     * This is the one place that mapping exists. A module awarding {@code WORKOUT_PR}
     * gets a PR celebration automatically; it never decides for itself which animation
     * plays, which is what non-negotiable #4 actually requires once there is more than
     * one producer of points in the app.
     */
    public static java.util.Optional<CelebrationType> typeFor(String ruleCode) {
        return switch (ruleCode) {
            case "WORKOUT_PR" -> java.util.Optional.of(CelebrationType.PR);
            case "HABIT_CONSISTENCY" -> java.util.Optional.of(CelebrationType.STREAK);
            case "RUN_MILESTONE", "RUN_FIRST_MILESTONE" -> java.util.Optional.of(CelebrationType.MILESTONE);
            case "HABIT_COMMITMENT" -> java.util.Optional.of(CelebrationType.ALL_HABITS_DONE);
            case "WORKOUT_SESSION_COMPLETE" -> java.util.Optional.of(CelebrationType.WORKOUT_COMPLETE);
            default -> java.util.Optional.empty();
        };
    }
}
