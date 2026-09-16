-- The goal module (spec §6 "goal", §5.4 "Goals", §8.6).
--
-- The spec's own data model splits this into goal + goal_target (a jsonb blob keyed by
-- kind). This folds the target into the goal row itself instead, as a small set of
-- typed nullable columns — only the columns a given kind actually uses are non-null —
-- because every kind's target shape is already known at compile time here; a jsonb
-- blob buys flexibility this module does not need yet, at the cost of a parse step on
-- every read. GoalService documents this trade where it computes progress.

CREATE TABLE goal (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    title          VARCHAR(120)  NOT NULL,
    description    VARCHAR(500),
    kind           VARCHAR(20)   NOT NULL,
    period_type    VARCHAR(20)   NOT NULL,
    start_date     DATE          NOT NULL,
    end_date       DATE,
    reward_points  INTEGER       NOT NULL DEFAULT 100,
    status         VARCHAR(20)   NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE,
    habit_id       UUID,
    exercise_id    UUID,
    target_value   NUMERIC(10, 2),
    current_value  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_goal PRIMARY KEY (id),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_kind CHECK (kind IN ('HABIT_ADHERENCE', 'EXERCISE_TARGET', 'RUN_DISTANCE', 'BODY_METRIC', 'CUSTOM')),
    CONSTRAINT ck_goal_period_type CHECK (period_type IN ('WEEK', 'MONTH', 'TARGET_DATE')),
    CONSTRAINT ck_goal_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'ARCHIVED')),
    CONSTRAINT ck_goal_reward_points CHECK (reward_points >= 0)
);

CREATE INDEX ix_goal_user_status ON goal (user_id, status);
CREATE INDEX ix_goal_user_end_date ON goal (user_id, end_date);
