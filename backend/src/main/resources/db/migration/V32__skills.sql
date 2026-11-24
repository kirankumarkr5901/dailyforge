-- Skill work from the Cut Block plan: handstand, crow + L-sit and flips, each in three
-- levels. The drills themselves live in code (SkillCatalog); the database only holds
-- where each user is and which days they did the block.

-- The level a user is working at, per skill. No row means level 1.
CREATE TABLE skill_progress (
    user_id     UUID        NOT NULL,
    skill_code  VARCHAR(20) NOT NULL,
    level       INTEGER     NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_skill_progress PRIMARY KEY (user_id, skill_code),
    CONSTRAINT fk_skill_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_skill_progress_level CHECK (level BETWEEN 1 AND 3)
);

-- One row per skill block done: a skill, a day, and the level it was done at.
CREATE TABLE skill_log (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    skill_code   VARCHAR(20) NOT NULL,
    occurred_on  DATE        NOT NULL,
    level        INTEGER     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_skill_log PRIMARY KEY (id),
    CONSTRAINT fk_skill_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_skill_log_day UNIQUE (user_id, skill_code, occurred_on)
);

CREATE INDEX ix_skill_log_user_date ON skill_log (user_id, occurred_on);

-- Paid once per skill per day when its block is marked done; reversed if unmarked.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000c0', NULL, 'SKILL_BLOCK',
 '{"points":5}', TRUE,
 'Paid once per skill per day when its skill block is marked done.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
