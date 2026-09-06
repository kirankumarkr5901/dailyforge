package com.dailyforge.points.domain;

/** Mirrors the CHECK constraint on {@code points_entry.category} (spec §5.1). */
public enum PointsCategory {
    WORKOUT,
    RUN,
    HABIT,
    ACTIVITY,
    GOAL,
    JOB,
    REWARD,
    ADJUSTMENT
}
