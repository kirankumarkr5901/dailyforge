package com.dailyforge.workout.domain;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deload weeks: a lighter week where every planned exercise's target sets are halved,
 * rounded up. It is a training choice for one week, not a change to the plan, so it
 * needs no "Enable edit" and leaves the plan itself untouched.
 */
@Service
public class DeloadService {

    private final JdbcTemplate jdbc;

    public DeloadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Half the sets, rounded up, so a single planned set stays one set. */
    public static Integer deloadSets(Integer targetSets) {
        return targetSets == null ? null : (targetSets + 1) / 2;
    }

    @Transactional(readOnly = true)
    public boolean isDeload(UUID userId, LocalDate date) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM deload_week WHERE user_id = ? AND week_start = ?",
                Integer.class,
                userId,
                WeeklyVolumeService.mondayOf(date));
        return n != null && n > 0;
    }

    /** Switches the week containing {@code date} on or off. Doing either twice is harmless. */
    @Transactional
    public void set(UUID userId, LocalDate date, boolean on) {
        LocalDate weekStart = WeeklyVolumeService.mondayOf(date);
        jdbc.update("DELETE FROM deload_week WHERE user_id = ? AND week_start = ?", userId, weekStart);
        if (on) {
            jdbc.update(
                    "INSERT INTO deload_week (user_id, week_start, created_at) VALUES (?, ?, ?)",
                    userId,
                    weekStart,
                    Timestamp.from(Instant.now()));
        }
    }
}
