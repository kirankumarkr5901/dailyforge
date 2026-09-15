package com.dailyforge.habit.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code date} is the resolved date this board is for — echoed back so a caller that
 * asked for "today" without knowing what date that is (spec §4.2: only the server
 * knows) can read it from the response instead of computing it itself.
 */
public record HabitBoard(LocalDate date, List<HabitBoardEntry> habits, String bonusHint) {}
