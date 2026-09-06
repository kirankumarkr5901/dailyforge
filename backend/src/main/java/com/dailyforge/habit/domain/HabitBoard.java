package com.dailyforge.habit.domain;

import java.util.List;

public record HabitBoard(List<HabitBoardEntry> habits, String bonusHint) {}
