-- The run module (spec §6 "run", §5.4 "Runs").
--
-- run_record is deliberately NOT a table here, the same call M4 made for
-- personal_record: bracket and overall rankings are pure display values, computed on
-- demand from run by ordering, not part of the points-invariant chain the way the
-- reconciled RUN_MILESTONE/RUN_FIRST_MILESTONE entries are.
--
-- Soft-deleted, like workout_set, for the same reason: ReconcileScope.RunPr's
-- sourceIdsInScope has to stay able to find a deleted run's row to reverse its stale
-- ledger entries. A hard delete removes the very row that reversal needs to be found
-- by, which is exactly the bug the habit module's own sourceIdsInScope javadoc warns
-- about — "must include every id ... whether or not it currently has a desired entry".

CREATE TABLE run (
    id                UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    occurred_on       DATE          NOT NULL,
    distance_meters   INTEGER       NOT NULL,
    duration_seconds  INTEGER       NOT NULL,
    type              VARCHAR(10)   NOT NULL,
    -- Derived and stored, not recomputed ad hoc, the same call workout_set's
    -- total_weight_kg made — stable even if the formula ever changes for new rows.
    pace_sec_per_km   INTEGER       NOT NULL,
    note              VARCHAR(280),
    felt_effort       INTEGER,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_run PRIMARY KEY (id),
    CONSTRAINT fk_run_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_run_type CHECK (type IN ('LONG', 'INTERVAL', 'TEMPO')),
    CONSTRAINT ck_run_distance CHECK (distance_meters > 0),
    CONSTRAINT ck_run_duration CHECK (duration_seconds > 0),
    CONSTRAINT ck_run_felt_effort CHECK (felt_effort IS NULL OR felt_effort BETWEEN 1 AND 5)
);

CREATE INDEX ix_run_user_date ON run (user_id, occurred_on DESC);
CREATE INDEX ix_run_user_distance ON run (user_id, distance_meters DESC, pace_sec_per_km ASC);
