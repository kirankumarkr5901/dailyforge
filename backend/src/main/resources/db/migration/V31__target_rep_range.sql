-- Target reps become a range, e.g. 8-12. target_reps is the low end; target_reps_max is
-- the top, or null for a single number. "Reps, then weight": once every set reaches the
-- top of the range, the next session goes up in weight.
ALTER TABLE plan_exercise ADD COLUMN target_reps_max INTEGER;
ALTER TABLE plan_exercise ADD CONSTRAINT ck_plan_exercise_rep_range
    CHECK (target_reps_max IS NULL OR target_reps IS NULL OR target_reps_max >= target_reps);
