package com.dailyforge.workout.domain;

/** Spec §5.5: how {@code enteredWeight} becomes {@code totalWeightKg}. */
public enum WeightMode {
    /** Per-hand — the entered weight is doubled. */
    SINGLE,
    /** As entered. */
    COMBINED
}
