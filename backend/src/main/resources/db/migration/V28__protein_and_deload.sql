-- Two pieces from the Cut Block plan: a daily protein meter, and deload weeks.

-- Protein points get their own line on Home, the way Forge 66 did.
ALTER TABLE points_entry DROP CONSTRAINT ck_points_entry_category;
ALTER TABLE points_entry ADD CONSTRAINT ck_points_entry_category CHECK (category IN
    ('WORKOUT', 'RUN', 'HABIT', 'ACTIVITY', 'GOAL', 'JOB', 'REWARD', 'BADGE', 'FORGE', 'NUTRITION', 'ADJUSTMENT'));

-- Paid once per day when the day's protein reaches the target; reversed if an undo
-- takes it back below.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000b0', NULL, 'PROTEIN_TARGET',
 '{"points":10}', TRUE,
 'Paid once per day when the protein meter reaches the daily target.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- One row per user, created on first use. foods_seeded records that the starter food
-- list was handed out once, so deleting every food does not bring the list back.
CREATE TABLE nutrition_settings (
    user_id           UUID    NOT NULL,
    protein_target_g  INTEGER NOT NULL DEFAULT 140,
    foods_seeded      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_nutrition_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_nutrition_settings_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_nutrition_settings_target CHECK (protein_target_g BETWEEN 1 AND 500)
);

-- The one-tap foods. Each user has their own editable copy.
CREATE TABLE protein_food (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    grams       INTEGER      NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_protein_food PRIMARY KEY (id),
    CONSTRAINT fk_protein_food_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_protein_food_grams CHECK (grams BETWEEN 1 AND 200)
);

CREATE INDEX ix_protein_food_user ON protein_food (user_id, sort_order);

-- What was eaten. The food's name and grams are copied, not referenced, so renaming or
-- deleting a food later never rewrites what a past day added up to.
CREATE TABLE protein_log (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    occurred_on  DATE        NOT NULL,
    food_name    VARCHAR(60) NOT NULL,
    grams        INTEGER     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_protein_log PRIMARY KEY (id),
    CONSTRAINT fk_protein_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_protein_log_grams CHECK (grams BETWEEN 1 AND 200)
);

CREATE INDEX ix_protein_log_user_date ON protein_log (user_id, occurred_on);

-- A week the user marked as a deload: target sets are halved on every workout that
-- week. Keyed by the Monday the week starts on.
CREATE TABLE deload_week (
    user_id     UUID NOT NULL,
    week_start  DATE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_deload_week PRIMARY KEY (user_id, week_start),
    CONSTRAINT fk_deload_week_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
