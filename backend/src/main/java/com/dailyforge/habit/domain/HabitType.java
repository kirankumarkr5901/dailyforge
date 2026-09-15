package com.dailyforge.habit.domain;

/**
 * A strict habit carries a penalty for missing a scheduled day; a normal one breaks its
 * streak on a miss but costs nothing (spec §5.4).
 */
public enum HabitType {
    NORMAL,
    STRICT
}
