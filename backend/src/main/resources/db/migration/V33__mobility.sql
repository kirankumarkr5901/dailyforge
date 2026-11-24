-- Guided mobility flows from the Cut Block plan. The flows themselves live in code
-- (MobilityCatalog); this only records which flows a user finished on which days.
CREATE TABLE mobility_log (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    flow_code    VARCHAR(20) NOT NULL,
    occurred_on  DATE        NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_mobility_log PRIMARY KEY (id),
    CONSTRAINT fk_mobility_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_mobility_log_day UNIQUE (user_id, flow_code, occurred_on)
);

CREATE INDEX ix_mobility_log_user_date ON mobility_log (user_id, occurred_on);

-- Paid once per flow per day when the whole flow is played through.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000d0', NULL, 'MOBILITY_FLOW',
 '{"points":5}', TRUE,
 'Paid once per flow per day when a guided mobility flow is played to the end.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
