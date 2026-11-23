-- Rest time per planned exercise, in seconds. Null means "use the default rest timer".
-- A heavy squat wants about three minutes between sets; a lateral raise, under one.
ALTER TABLE plan_exercise ADD COLUMN rest_seconds INTEGER;
ALTER TABLE plan_exercise ADD CONSTRAINT ck_plan_exercise_rest CHECK (rest_seconds IS NULL OR rest_seconds BETWEEN 0 AND 900);
