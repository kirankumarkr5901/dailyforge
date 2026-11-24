-- Challenges: "Plank for 30 days straight". Each one owns a daily habit (the tick); the
-- challenge tracks the current run of consecutive done days. A missed day restarts the
-- run at day 1. Reaching the length waits for an explicit claim, which pays the bonus.
CREATE TABLE challenge (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    habit_id       UUID         NOT NULL,
    title          VARCHAR(80)  NOT NULL,
    length_days    INTEGER      NOT NULL,
    bonus_points   INTEGER      NOT NULL,
    started_on     DATE         NOT NULL,
    -- Day 1 of the current run: moves forward every time a missed day restarts it.
    run_start      DATE         NOT NULL,
    restarts       INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(10)  NOT NULL,
    reached_on     DATE,
    claimed_at     TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_challenge PRIMARY KEY (id),
    CONSTRAINT fk_challenge_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_challenge_habit FOREIGN KEY (habit_id) REFERENCES habit (id) ON DELETE CASCADE,
    CONSTRAINT ck_challenge_length CHECK (length_days BETWEEN 2 AND 365),
    CONSTRAINT ck_challenge_bonus CHECK (bonus_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_challenge_status CHECK (status IN ('ACTIVE', 'REACHED', 'COMPLETED', 'ABANDONED'))
);

CREATE INDEX ix_challenge_user ON challenge (user_id, status);
