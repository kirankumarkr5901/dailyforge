package com.dailyforge.habit.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The bitmask that lets a habit be "weekdays only" instead of failing every weekend
 * (spec §6). Bit 0 is Monday, matching the app's fixed Monday week start, through bit 6
 * for Sunday. All seven bits set (127) is the default — every day.
 */
public final class ScheduleDays {

    public static final int EVERY_DAY = 0b111_1111;

    private ScheduleDays() {}

    public static boolean isScheduled(int scheduleDays, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        int bit = day.getValue() - 1; // Monday = 1 -> bit 0
        return (scheduleDays & (1 << bit)) != 0;
    }

    public static void validate(int scheduleDays) {
        if (scheduleDays < 1 || scheduleDays > EVERY_DAY) {
            throw new IllegalArgumentException("scheduleDays must be between 1 and 127");
        }
    }
}
