-- Adds CABLE to the equipment enum (owner feedback: cable machines are common and
-- distinct from free-weight MACHINE stations).

-- The exercise table's own CHECK constraint enumerates every valid equipment value
-- (see V5__workouts.sql) — without widening it, inserting or updating any exercise
-- with equipment='CABLE' fails the constraint outright.
ALTER TABLE exercise DROP CONSTRAINT ck_exercise_equipment;
ALTER TABLE exercise ADD CONSTRAINT ck_exercise_equipment
    CHECK (equipment IN ('DUMBBELL', 'BARBELL', 'BODYWEIGHT', 'MACHINE', 'CABLE', 'NONE'));

-- PR type-factor config: a cable stack is a fixed resistance path the same way a
-- machine is, just via a pulley rather than a lever, so it earns the same PR weight as
-- MACHINE's own 0.9.
--
-- config_json is a plain VARCHAR, not a JSON column type (see V1__baseline.sql), so
-- this is a string replace rather than a JSON function — the same reason the seed row
-- in V2 is itself a literal string.
UPDATE points_rule_config
SET config_json = REPLACE(config_json, '"MACHINE":0.9,"BODYWEIGHT":1.0', '"MACHINE":0.9,"CABLE":0.9,"BODYWEIGHT":1.0'),
    updated_at = CURRENT_TIMESTAMP
WHERE rule_code = 'WORKOUT_PR'
  AND user_id IS NULL;
