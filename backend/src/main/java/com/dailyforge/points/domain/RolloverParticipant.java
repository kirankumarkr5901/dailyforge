package com.dailyforge.points.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A module that has work to do when a user's local day closes (spec §5.6): applying
 * strict-habit penalties, settling future-dated logs, evaluating the commitment bonus,
 * breaking streaks. Nothing implements this yet — habits arrive at M3 — but the
 * extension point and the job that drives it exist now, proven by
 * {@code DailyRolloverJobTest}, so M3 only has to write the habit-specific logic.
 *
 * Every implementation must be idempotent: {@link com.dailyforge.points.domain.DailyRolloverJob}
 * guarantees it calls this once per user per closed date, never twice, but a
 * participant that is not idempotent on its own is one bug away from double-charging a
 * penalty if that guarantee is ever tested by a retry.
 */
public interface RolloverParticipant {

    void onDayClosed(UUID userId, LocalDate closedDate);
}
