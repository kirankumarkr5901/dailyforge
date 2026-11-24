package com.dailyforge.challenge.domain;

import java.time.LocalDate;
import java.util.Set;

/**
 * The rule that makes a challenge "N days straight", free of the database.
 *
 * <p>Walking from the run's day 1 up to yesterday, every closed day must be done. A
 * closed day that is not (missed, or skipped) restarts the run: day 1 becomes the day
 * after it. Today is still open, so it can only add to the run, never break it.
 */
public final class ChallengeRun {

    private ChallengeRun() {}

    /** Where the run starts now, its day number today, and how many days in a row are done. */
    public record Settled(LocalDate runStart, int dayNumber, int doneInARow, boolean restarted) {}

    public static Settled settle(LocalDate runStart, LocalDate today, Set<LocalDate> doneDays) {
        LocalDate start = runStart;
        boolean restarted = false;
        for (LocalDate d = runStart; d.isBefore(today); d = d.plusDays(1)) {
            if (!doneDays.contains(d)) {
                start = d.plusDays(1);
                restarted = true;
            }
        }
        int done = 0;
        for (LocalDate d = start; !d.isAfter(today) && doneDays.contains(d); d = d.plusDays(1)) {
            done++;
        }
        int dayNumber = (int) (today.toEpochDay() - start.toEpochDay()) + 1;
        return new Settled(start, dayNumber, done, restarted);
    }
}
