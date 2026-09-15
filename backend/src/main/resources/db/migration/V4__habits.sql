-- The habit module (spec §6 "habit", §5.4 "Habits").
--
-- habit_log is NOT append-only like points_entry: it is the raw source data the
-- reconciler reads to decide what the ledger should contain, so ticking and unticking a
-- day is a real UPDATE here. The ledger itself is what stays immutable — reconciliation
-- is exactly the mechanism that keeps it correct as this table changes underneath it.
--
-- habit_streak is a cache, in the same spirit as user_score_cache: never read as the
-- source of truth for a bonus decision, only for fast display, and always rebuildable
-- from habit_log by the reconciler.

CREATE TABLE habit (
    id                UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    icon              VARCHAR(40)  NOT NULL,
    points            INTEGER      NOT NULL,
    type              VARCHAR(10)  NOT NULL,
    penalty_points    INTEGER      NOT NULL DEFAULT 0,
    base_bonus        INTEGER      NOT NULL,
    bonus_multiplier  NUMERIC(4, 2) NOT NULL,
    -- Bit 0 = Monday .. bit 6 = Sunday (spec's fixed Monday week start). All 7 bits set
    -- by default, so an ordinary habit is scheduled every day until the user says
    -- otherwise (spec's own addition, §6: "lets a habit be weekdays only").
    schedule_days     INTEGER      NOT NULL DEFAULT 127,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    active_from       DATE         NOT NULL,
    archived_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_habit PRIMARY KEY (id),
    CONSTRAINT fk_habit_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_habit_type CHECK (type IN ('NORMAL', 'STRICT')),
    CONSTRAINT ck_habit_points CHECK (points >= 0),
    CONSTRAINT ck_habit_penalty_points CHECK (penalty_points >= 0),
    CONSTRAINT ck_habit_schedule_days CHECK (schedule_days BETWEEN 1 AND 127)
);

CREATE INDEX ix_habit_user ON habit (user_id, archived_at, sort_order);

CREATE TABLE habit_log (
    id           UUID        NOT NULL,
    habit_id     UUID        NOT NULL,
    occurred_on  DATE        NOT NULL,
    state        VARCHAR(10) NOT NULL,
    logged_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_habit_log PRIMARY KEY (id),
    CONSTRAINT fk_habit_log_habit FOREIGN KEY (habit_id) REFERENCES habit (id) ON DELETE CASCADE,
    CONSTRAINT uq_habit_log_habit_date UNIQUE (habit_id, occurred_on),
    CONSTRAINT ck_habit_log_state CHECK (state IN ('DONE', 'SKIPPED'))
);

CREATE INDEX ix_habit_log_habit_date ON habit_log (habit_id, occurred_on);

-- Derived and rebuilt by the reconciler; never written to directly by a request handler.
CREATE TABLE habit_streak (
    habit_id                   UUID    NOT NULL,
    current_streak             INTEGER NOT NULL DEFAULT 0,
    best_streak                INTEGER NOT NULL DEFAULT 0,
    last_awarded_multiple_of7  INTEGER NOT NULL DEFAULT 0,
    last_completed_date        DATE,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_habit_streak PRIMARY KEY (habit_id),
    CONSTRAINT fk_habit_streak_habit FOREIGN KEY (habit_id) REFERENCES habit (id) ON DELETE CASCADE
);
