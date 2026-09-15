package com.dailyforge.habit.domain;

import java.util.UUID;

public record HabitBoardEntry(
        UUID id,
        String name,
        String icon,
        int points,
        HabitType type,
        HabitDayState state,
        /** Only meaningful when {@code state} is PLANNED: whether it has been pre-ticked. */
        boolean plannedDone,
        int currentStreak,
        int bestStreak,
        boolean editable) {}
