package com.dailyforge.insight.domain;

/** The heatmap cell state (spec §8.1.1). */
public enum DayState {
    BOTH,
    WORKOUT,
    RUN,
    REST,
    MISSED,
    /** Future date, or before the account started. */
    EMPTY
}
