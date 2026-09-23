-- The body module (spec §6 "body", §8.8). No points anywhere here, per the plan.
--
-- One row per user per date: the API has no PATCH endpoint (spec §7's own list), so a
-- second POST for a date that already has an entry updates it in place rather than
-- erroring — the natural "upsert by date" reading of that omission.

CREATE TABLE body_metric (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    occurred_on    DATE          NOT NULL,
    weight_kg      NUMERIC(5, 2) NOT NULL,
    -- Per-log override; falls back to user_settings.height_cm when absent (spec §8.8:
    -- "store it on settings and allow a per-log override").
    height_cm      NUMERIC(5, 1),
    body_fat_pct   NUMERIC(4, 1),
    note           VARCHAR(280),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_body_metric PRIMARY KEY (id),
    CONSTRAINT fk_body_metric_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_body_metric_user_date UNIQUE (user_id, occurred_on),
    CONSTRAINT ck_body_metric_weight CHECK (weight_kg > 0),
    CONSTRAINT ck_body_metric_body_fat CHECK (body_fat_pct IS NULL OR body_fat_pct BETWEEN 0 AND 100)
);

CREATE INDEX ix_body_metric_user_date ON body_metric (user_id, occurred_on DESC);
