-- Widen the goal period check constraint for the new YEAR option (owner feedback,
-- "milestones for every month, yearly" — the user-defined-goal half of that request
-- reuses this existing column rather than a parallel entity).
ALTER TABLE goal DROP CONSTRAINT ck_goal_period_type;
ALTER TABLE goal ADD CONSTRAINT ck_goal_period_type CHECK (period_type IN ('WEEK', 'MONTH', 'YEAR', 'TARGET_DATE'));
