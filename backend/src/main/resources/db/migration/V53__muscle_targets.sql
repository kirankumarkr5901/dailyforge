-- Weekly set targets per main muscle (owner request: a realistic number for each muscle,
-- not 10-20 for all). The AI sets them from the person's training; they can be edited, and
-- an edited one is never overwritten. Hitting a muscle's minimum in a week pays 5 points,
-- and every main muscle reaching its minimum pays 25 more.
CREATE TABLE muscle_target (
    user_id     UUID         NOT NULL,
    muscle      VARCHAR(20)  NOT NULL,
    min_sets    INTEGER      NOT NULL,
    max_sets    INTEGER      NOT NULL,
    source      VARCHAR(8)   NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_muscle_target PRIMARY KEY (user_id, muscle),
    CONSTRAINT fk_muscle_target_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_muscle_target_range CHECK (min_sets BETWEEN 1 AND 40 AND max_sets BETWEEN min_sets AND 50),
    CONSTRAINT ck_muscle_target_source CHECK (source IN ('DEFAULT', 'AI', 'USER'))
);

INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000c1', NULL, 'MUSCLE_TARGET',
 '{"points":5}', TRUE,
 'A main muscle reaching its weekly minimum sets.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0000-4000-8000-0000000000c2', NULL, 'MUSCLE_TARGET_ALL',
 '{"points":25}', TRUE,
 'Every main muscle reaching its weekly minimum sets.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
