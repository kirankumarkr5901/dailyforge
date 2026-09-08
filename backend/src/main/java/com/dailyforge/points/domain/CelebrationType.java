package com.dailyforge.points.domain;

/**
 * Which animation the frontend plays (spec §5.2, §9.4). The engine decides this from the
 * rule code that was awarded — the frontend is only ever told what happened, never asked
 * to infer it from a raw amount.
 */
public enum CelebrationType {
    PR,
    STREAK,
    MILESTONE,
    ALL_HABITS_DONE,
    WORKOUT_COMPLETE
}
