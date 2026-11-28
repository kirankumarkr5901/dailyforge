-- Forge 66 boss fights: each week a boss built from the weakest stat among that week's
-- quest areas. Beat it by finishing every quest in its area that week. An escaped boss
-- comes back the next week one level stronger, for a bigger reward.
CREATE TABLE forge_boss (
    id           UUID          NOT NULL,
    user_id      UUID          NOT NULL,
    program_id   UUID          NOT NULL,
    week_start   DATE          NOT NULL,
    area         VARCHAR(12)   NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    description  VARCHAR(300)  NOT NULL,
    level        INTEGER       NOT NULL,
    reward_xp    INTEGER       NOT NULL,
    status       VARCHAR(10)   NOT NULL,
    defeated_on  DATE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_forge_boss PRIMARY KEY (id),
    CONSTRAINT fk_forge_boss_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_forge_boss_program FOREIGN KEY (program_id) REFERENCES forge_program (id) ON DELETE CASCADE,
    CONSTRAINT uq_forge_boss_week UNIQUE (program_id, week_start),
    CONSTRAINT ck_forge_boss_area CHECK (area IN ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL')),
    CONSTRAINT ck_forge_boss_status CHECK (status IN ('ACTIVE', 'DEFEATED', 'ESCAPED')),
    CONSTRAINT ck_forge_boss_level CHECK (level >= 1),
    CONSTRAINT ck_forge_boss_reward CHECK (reward_xp > 0)
);

CREATE INDEX ix_forge_boss_user ON forge_boss (user_id, week_start);

INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000a5', NULL, 'FORGE_BOSS',
 '{"baseXp":200,"xpPerReturn":100}', TRUE,
 'Forge 66 boss: XP for beating the week''s boss (more each time it returns); paid into the main score at the quest rate.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
