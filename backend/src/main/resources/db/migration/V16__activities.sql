-- The activity module (spec §6 "activity"): positive and negative one-off actions
-- outside the structured habit/workout/run/job trackers — the plan's own dictionary
-- named this module from the start, but it was never built through M1-M8.

CREATE TABLE activity_type (
    id           UUID          NOT NULL,
    user_id      UUID          NOT NULL,
    name         VARCHAR(120)  NOT NULL,
    polarity     VARCHAR(10)   NOT NULL,
    points       INTEGER       NOT NULL,
    icon         VARCHAR(40)   NOT NULL,
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    archived_at  TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_type PRIMARY KEY (id),
    CONSTRAINT fk_activity_type_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_activity_type_polarity CHECK (polarity IN ('POSITIVE', 'NEGATIVE')),
    CONSTRAINT ck_activity_type_points CHECK (points > 0)
);

CREATE INDEX ix_activity_type_user ON activity_type (user_id, archived_at);

-- No unique (activity_type_id, occurred_on): unlike a habit, the same activity can
-- happen more than once in a day (two separate logs, or one log with count > 1) —
-- "meditated" is not a once-per-day checkbox the way a habit tick is.
CREATE TABLE activity_log (
    id                UUID          NOT NULL,
    activity_type_id  UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    occurred_on       DATE          NOT NULL,
    count             INTEGER       NOT NULL DEFAULT 1,
    note              VARCHAR(500),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_log PRIMARY KEY (id),
    CONSTRAINT fk_activity_log_type FOREIGN KEY (activity_type_id) REFERENCES activity_type (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_activity_log_count CHECK (count > 0)
);

CREATE INDEX ix_activity_log_user ON activity_log (user_id, occurred_on DESC);
CREATE INDEX ix_activity_log_type ON activity_log (activity_type_id);
