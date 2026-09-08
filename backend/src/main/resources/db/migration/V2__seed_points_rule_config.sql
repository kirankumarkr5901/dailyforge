-- System defaults for every point value in the app.
--
-- Nothing in Java may hardcode a points number (spec §5.4). A row with user_id NULL is
-- the system default; a row with a user_id overrides it for that user. The values here
-- are the spec's defaults and the §13 decisions, so changing the economy is a migration
-- rather than a code change.

INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES

-- Habits -------------------------------------------------------------------
('0b1f4a10-0000-4000-8000-000000000001', NULL, 'HABIT_BASE',
 '{"source":"habit.points"}', TRUE,
 'Points for ticking a habit. Set per habit by the user.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000002', NULL, 'HABIT_PENALTY',
 '{"source":"habit.penaltyPoints","appliesTo":"STRICT","scheduledDaysOnly":true}', TRUE,
 'Charged at rollover when a strict habit was not completed on a scheduled day.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- bonus(n) = round(baseBonus * bonusMultiplier^(n-1)), n = streak/7, n capped at 12.
-- The cap is decision §13.4: uncapped, a 1.5 multiplier pays millions inside a year.
('0b1f4a10-0000-4000-8000-000000000003', NULL, 'HABIT_CONSISTENCY',
 '{"periodDays":7,"defaultBaseBonus":20,"defaultMultiplier":1.5,"maxExponent":12}', TRUE,
 'Streak bonus paid at every multiple of 7 days.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000004', NULL, 'HABIT_COMMITMENT',
 '{"source":"settings.commitmentBonus"}', TRUE,
 'Paid when every habit scheduled for that day is complete.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Runs ---------------------------------------------------------------------
('0b1f4a10-0000-4000-8000-000000000010', NULL, 'RUN_DISTANCE',
 '{"metresPerPoint":100}', TRUE,
 'One point per 100 m, floored.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Highest single milestone only (§13.3). 21.1 and 42.2 km are the half and full
-- marathon readings of the plan's "21" and "42" (§13.1).
('0b1f4a10-0000-4000-8000-000000000011', NULL, 'RUN_MILESTONE',
 '{"mode":"HIGHEST_ONLY","milestones":[{"metres":10000,"points":50},{"metres":15000,"points":80},{"metres":21100,"points":150},{"metres":25000,"points":180},{"metres":42200,"points":400},{"metres":50000,"points":500}]}', TRUE,
 'Milestone bonus for the highest distance threshold a run crosses.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000012', NULL, 'RUN_FIRST_MILESTONE',
 '{"multiplier":1}', TRUE,
 'Pays the milestone value again the first time it is ever reached.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Workouts -----------------------------------------------------------------
('0b1f4a10-0000-4000-8000-000000000020', NULL, 'WORKOUT_SET',
 '{"points":1}', TRUE,
 'Per logged set. Configurable to 0 (§13.2).',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000021', NULL, 'WORKOUT_PR',
 '{"formula":"round(typeFactor * (totalWeightKg / 5)) + repsOnlyBonus","weightDivisor":5,"repsOnlyBonus":5,"minAward":1,"maxAward":100,"typeFactor":{"BARBELL":1.2,"DUMBBELL":1.1,"MACHINE":0.9,"BODYWEIGHT":1.0}}', TRUE,
 'Personal record bonus.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000022', NULL, 'WORKOUT_SESSION_COMPLETE',
 '{"points":0}', TRUE,
 'Celebration only, worth zero points, per the plan.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Activities, goals, rewards -----------------------------------------------
('0b1f4a10-0000-4000-8000-000000000030', NULL, 'ACTIVITY_POSITIVE',
 '{"source":"activity.points"}', TRUE, 'User-defined positive activity.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000031', NULL, 'ACTIVITY_NEGATIVE',
 '{"source":"activity.points","sign":-1}', TRUE, 'User-defined negative activity.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000040', NULL, 'GOAL_COMPLETE',
 '{"source":"goal.rewardPoints","default":100}', TRUE,
 'Paid on goal completion, reversed if the goal is reopened.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Zero by default (§13.9): job activity is trivially inflatable.
('0b1f4a10-0000-4000-8000-000000000050', NULL, 'JOB_STAGE_ADVANCE',
 '{"points":0}', FALSE, 'Disabled by default.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000060', NULL, 'REWARD_REDEEM',
 '{"source":"reward.cost","sign":-1}', TRUE, 'Spending points on a reward.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Guardrails (§5.7). Not accusations — limits that keep the economy meaningful.
('0b1f4a10-0000-4000-8000-000000000070', NULL, 'DAILY_CAP',
 '{"WORKOUT":500,"RUN":400,"HABIT":null,"ACTIVITY":null}', TRUE,
 'Daily points cap per category. Null means uncapped.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000071', NULL, 'SANITY_LIMITS',
 '{"runMaxMetres":200000,"runMaxSeconds":86400,"setMaxWeightKg":500,"setMaxReps":100}', TRUE,
 'Input sanity limits, enforced in the domain layer.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0000-4000-8000-000000000072', NULL, 'EDIT_WINDOW',
 '{"habitDays":1,"generalDays":7}', TRUE,
 'Habits: today and yesterday. Everything else: 7 days (§13.6).',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
