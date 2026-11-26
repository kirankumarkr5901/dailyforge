-- Water: one running total per user per day, in millilitres, against a daily target.
CREATE TABLE water_log (
    user_id      UUID    NOT NULL,
    occurred_on  DATE    NOT NULL,
    ml           INTEGER NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_water_log PRIMARY KEY (user_id, occurred_on),
    CONSTRAINT fk_water_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_water_log_ml CHECK (ml BETWEEN 0 AND 10000)
);

ALTER TABLE nutrition_settings ADD COLUMN water_target_ml INTEGER NOT NULL DEFAULT 3000;
ALTER TABLE nutrition_settings ADD CONSTRAINT ck_nutrition_settings_water CHECK (water_target_ml BETWEEN 500 AND 8000);

-- Paid once a day when the day's water reaches the target; taken back if undone below it.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000b2', NULL, 'WATER_TARGET',
 '{"points":15}', TRUE,
 'Paid once a day when the water tracker reaches the daily target.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
