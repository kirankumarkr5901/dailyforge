-- Optimistic concurrency for every row a user can edit from more than one device.
--
-- The problem this solves: the app is used from a desktop and a phone. A device that
-- has been sitting on a page for days holds an old copy of a row. Without a version,
-- its next save is a full overwrite that silently discards everything the other device
-- changed in the meantime — the edit is accepted, and the newer data is simply gone.
--
-- Every write to these tables now carries the version the client last read. The server
-- compares it and refuses the write if the row has moved on, so the loser of a race is
-- told to re-read rather than being allowed to erase the winner.
--
-- Deliberately not applied to:
--   * goal          — GoalService.list() recomputes and saves progress on every read,
--                     so its version would advance without anyone editing anything and
--                     every legitimate edit would conflict.
--   * habit_log, workout_session, points_entry, activity_log — append-only or
--                     server-owned; there is no "edit this row" path to lose a race on.
--   * habit_streak, user_score_cache, user_rollover_state — derived caches the server
--                     rebuilds; a stale client never writes them.

ALTER TABLE habit ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_settings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE job_application ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reward ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE run ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE exercise ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workout_set ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workout_plan ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
