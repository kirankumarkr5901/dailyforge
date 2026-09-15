-- The workout module (spec §6 "workout", §5.4 "Workouts", §5.5 "PR definition").
--
-- Unlike habit_log, workout_set rows carry their own real id and are the source_id the
-- points ledger points back to directly — no deterministic hashing is needed the way
-- habit-day pairs required one, because every set already has a row of its own.
--
-- personal_record is deliberately NOT a table here: recent/lifetime PR are pure display
-- values (spec §5.5), computed on demand from workout_set by ordering, not part of the
-- points-invariant chain the way habit_streak is. Only the single current lifetime-best
-- set per (user, exercise) matters to the ledger, and that is reconciled the same way a
-- habit's streak bonus is (see WorkoutExerciseReconciliationCalculator) — reversed off
-- the set that used to hold it, awarded to whichever set holds it now.

CREATE TABLE exercise (
    id             UUID          NOT NULL,
    -- NULL means the shared system catalog (spec §6: "exercises are shared across
    -- plans"); a non-null value is a user's own custom exercise, visible only to them.
    owner_user_id  UUID,
    name           VARCHAR(120)  NOT NULL,
    search_name    VARCHAR(120)  NOT NULL,
    kind           VARCHAR(10)   NOT NULL,
    equipment      VARCHAR(12)   NOT NULL,
    muscle_groups  VARCHAR(200)  NOT NULL DEFAULT '',
    is_elite       BOOLEAN       NOT NULL DEFAULT FALSE,
    archived_at    TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_exercise PRIMARY KEY (id),
    CONSTRAINT fk_exercise_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_exercise_kind CHECK (kind IN ('STRENGTH', 'CARDIO')),
    CONSTRAINT ck_exercise_equipment CHECK (equipment IN ('DUMBBELL', 'BARBELL', 'BODYWEIGHT', 'MACHINE', 'NONE'))
);

CREATE INDEX ix_exercise_search ON exercise (search_name);
CREATE INDEX ix_exercise_owner ON exercise (owner_user_id, archived_at);

CREATE TABLE workout_plan (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    day_count   INTEGER      NOT NULL,
    -- Day labels, "|"-joined ("Push|Pull|Legs"). A day past the stored list falls back
    -- to "Day N" — see WorkoutPlan.labelFor. Kept inline rather than a child table: a
    -- plan's day labels are edited as a unit and never queried independently of it.
    day_labels  VARCHAR(400) NOT NULL DEFAULT '',
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workout_plan PRIMARY KEY (id),
    CONSTRAINT fk_workout_plan_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_workout_plan_day_count CHECK (day_count BETWEEN 1 AND 14)
);

CREATE INDEX ix_workout_plan_user ON workout_plan (user_id, archived_at);

-- day_index 0 is the "Extras" bucket (spec §8.2): exercises attached to a plan but not
-- assigned to any specific day yet, used as the source and target for swaps.
CREATE TABLE plan_exercise (
    id           UUID         NOT NULL,
    plan_id      UUID         NOT NULL,
    exercise_id  UUID         NOT NULL,
    day_index    INTEGER      NOT NULL,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    target_sets  INTEGER,
    target_reps  INTEGER,
    notes        VARCHAR(200),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_plan_exercise PRIMARY KEY (id),
    CONSTRAINT fk_plan_exercise_plan FOREIGN KEY (plan_id) REFERENCES workout_plan (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_exercise_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE CASCADE,
    -- An elite exercise (spec §8.2) is placed on several days as separate rows sharing
    -- the same exercise_id, which is why this is NOT unique on (plan_id, exercise_id)
    -- alone — but never twice on the same day.
    CONSTRAINT uq_plan_exercise_day UNIQUE (plan_id, exercise_id, day_index),
    CONSTRAINT ck_plan_exercise_day_index CHECK (day_index >= 0)
);

CREATE INDEX ix_plan_exercise_plan_day ON plan_exercise (plan_id, day_index, sort_order);

-- One row per (user, date, plan, day) — "get or create the session for this date" is
-- how the tracker opens (spec: GET /workouts/session?date=&planId=&dayIndex=).
CREATE TABLE workout_session (
    id           UUID    NOT NULL,
    user_id      UUID    NOT NULL,
    occurred_on  DATE    NOT NULL,
    plan_id      UUID,
    day_index    INTEGER,
    started_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_workout_session PRIMARY KEY (id),
    CONSTRAINT fk_workout_session_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_workout_session_plan FOREIGN KEY (plan_id) REFERENCES workout_plan (id) ON DELETE SET NULL
);

-- H2 and Postgres both treat NULL as distinct in a unique index, so a freeform session
-- (plan_id/day_index both NULL) never collides with another freeform session on the
-- same date — each such POST simply starts a new one, which is the intended behaviour
-- for logging without a plan.
CREATE UNIQUE INDEX uq_workout_session ON workout_session (user_id, occurred_on, plan_id, day_index);

CREATE TABLE workout_set (
    id               UUID           NOT NULL,
    session_id       UUID           NOT NULL,
    exercise_id      UUID           NOT NULL,
    set_number       INTEGER        NOT NULL,
    entered_weight   NUMERIC(6, 2)  NOT NULL DEFAULT 0,
    weight_mode      VARCHAR(10)    NOT NULL,
    added_weight     NUMERIC(6, 2),
    reps             INTEGER        NOT NULL,
    total_weight_kg  NUMERIC(6, 2)  NOT NULL,
    logged_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_workout_set PRIMARY KEY (id),
    CONSTRAINT fk_workout_set_session FOREIGN KEY (session_id) REFERENCES workout_session (id) ON DELETE CASCADE,
    -- CASCADE, not RESTRICT: a hard-deleted exercise (ExerciseService only does this
    -- when it has zero sets) never hits this path in practice, but deleting a whole
    -- user must be able to remove their exercise catalog rows and everything logged
    -- against them together, in one statement, the same way every other module cascades
    -- from app_user.
    CONSTRAINT fk_workout_set_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT ck_workout_set_weight_mode CHECK (weight_mode IN ('SINGLE', 'COMBINED')),
    CONSTRAINT ck_workout_set_reps CHECK (reps >= 0)
);

CREATE INDEX ix_workout_set_session ON workout_set (session_id, deleted_at);
CREATE INDEX ix_workout_set_exercise ON workout_set (exercise_id, deleted_at, total_weight_kg DESC, reps DESC);
