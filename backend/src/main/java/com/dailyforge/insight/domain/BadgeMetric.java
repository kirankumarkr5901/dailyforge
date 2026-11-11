package com.dailyforge.insight.domain;

/**
 * Which number a badge is scored against.
 *
 * Every one of these already exists on {@link MilestoneService.Recap} — a badge invents
 * no new tracking, it puts a threshold and a name on a figure the recap already
 * computes. That is deliberate: a badge that counted something of its own could
 * disagree with the recap sitting directly above it on the same page.
 */
public enum BadgeMetric {
    TOTAL_POINTS,
    WORKOUT_DAYS,
    RUN_DAYS,
    RUN_DISTANCE_METERS,
    HABITS_COMPLETED,
    GOALS_COMPLETED
}
