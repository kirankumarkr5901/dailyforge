-- Badges: a month or a year of work, recognised and paid out (owner request).
--
-- A badge is not a new kind of tracking. Every number it is scored against already
-- exists in the monthly and yearly recap — workout days, habits completed, distance
-- run, points earned. A badge is a threshold over one of those, with a name, and a
-- payout for crossing it.
--
-- Two tables, because they answer different questions. `badge` is the catalogue: what
-- exists, what it takes, what it pays. It is seeded here rather than written in Java so
-- that rebalancing is a migration, exactly like every other point value in the app.
-- `badge_award` is what a given user has actually claimed, and when.

-- Badge payouts are points like any other, and points carry a category. A badge is not
-- a workout or a goal or an adjustment — it is its own kind of earning, and collapsing
-- it into a neighbouring category would make the Home breakdown quietly wrong.
ALTER TABLE points_entry DROP CONSTRAINT ck_points_entry_category;
ALTER TABLE points_entry ADD CONSTRAINT ck_points_entry_category CHECK (category IN
    ('WORKOUT', 'RUN', 'HABIT', 'ACTIVITY', 'GOAL', 'JOB', 'REWARD', 'BADGE', 'ADJUSTMENT'));

CREATE TABLE badge (
    id           UUID          NOT NULL,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    -- What kind of badge this is, in one line: shown when the badge is opened.
    description  VARCHAR(300)  NOT NULL,
    -- What to actually do to earn it, in the user's own terms rather than as a formula.
    criteria     VARCHAR(300)  NOT NULL,
    period       VARCHAR(10)   NOT NULL,
    metric       VARCHAR(30)   NOT NULL,
    threshold    INTEGER       NOT NULL,
    points       INTEGER       NOT NULL,
    icon         VARCHAR(40)   NOT NULL,
    sort_order   INTEGER       NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_badge PRIMARY KEY (id),
    CONSTRAINT uq_badge_code UNIQUE (code),
    CONSTRAINT ck_badge_period CHECK (period IN ('MONTH', 'YEAR')),
    CONSTRAINT ck_badge_metric CHECK (metric IN
        ('TOTAL_POINTS', 'WORKOUT_DAYS', 'RUN_DAYS', 'RUN_DISTANCE_METERS', 'HABITS_COMPLETED', 'GOALS_COMPLETED')),
    CONSTRAINT ck_badge_threshold CHECK (threshold > 0),
    CONSTRAINT ck_badge_points CHECK (points > 0)
);

-- One row per badge a user has claimed, per period.
--
-- period_start is what makes a badge repeatable without being repeatable twice: the
-- same badge can be earned every month, and the unique constraint is what stops March's
-- badge being claimed a second time while still allowing April's.
CREATE TABLE badge_award (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    badge_code     VARCHAR(40)   NOT NULL,
    period_start   DATE          NOT NULL,
    period_end     DATE          NOT NULL,
    -- Recorded for history: the catalogue can be rebalanced later, and what was paid at
    -- the time should not silently change with it.
    points_awarded INTEGER       NOT NULL,
    claimed_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_badge_award PRIMARY KEY (id),
    CONSTRAINT fk_badge_award_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_badge_award_badge FOREIGN KEY (badge_code) REFERENCES badge (code),
    CONSTRAINT uq_badge_award_period UNIQUE (user_id, badge_code, period_start)
);

CREATE INDEX ix_badge_award_user ON badge_award (user_id, period_start);

-- The rule code every badge payout is written under. The amount comes from the badge
-- row, the same way a goal's payout comes from the goal — no number in Java.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-000000000090', NULL, 'BADGE_CLAIM',
 '{"source":"badge.points"}', TRUE,
 'Paid when a user claims a badge they have earned. The amount is the badge''s own payout.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- The starting catalogue.
--
-- Thresholds are set to be reachable but not automatic: the monthly ones sit near a
-- consistent-but-human month (fifteen workout days is roughly every other day), and the
-- yearly ones are what a genuinely sustained year looks like rather than a perfect one.
INSERT INTO badge (id, code, name, description, criteria, period, metric, threshold, points, icon, sort_order, created_at, updated_at) VALUES

('0b1f4a10-0001-4000-8000-000000000001', 'MONTH_IRON', 'Iron Month',
 'Awarded for a month of showing up to train, whatever else was going on.',
 'Log a workout on 15 separate days this month.',
 'MONTH', 'WORKOUT_DAYS', 15, 250, 'dumbbell', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000002', 'MONTH_STEADY', 'Steady Month',
 'Awarded for keeping your habits through a whole month rather than the first week of one.',
 'Complete 60 habit ticks this month.',
 'MONTH', 'HABITS_COMPLETED', 60, 250, 'check', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000003', 'MONTH_DISTANCE', 'Ground Covered',
 'Awarded for the distance a month of running adds up to.',
 'Run 40 km in total this month.',
 'MONTH', 'RUN_DISTANCE_METERS', 40000, 250, 'footprints', 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000004', 'MONTH_HAUL', 'Big Month',
 'Awarded for a month that was simply a lot of work, in whatever form you did it.',
 'Earn 3,000 points this month.',
 'MONTH', 'TOTAL_POINTS', 3000, 300, 'flame', 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000005', 'YEAR_IRON', 'Iron Year',
 'Awarded for a year of training that held together across every season of it.',
 'Log a workout on 150 separate days this year.',
 'YEAR', 'WORKOUT_DAYS', 150, 2500, 'dumbbell', 110, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000006', 'YEAR_STEADY', 'Year of Habits',
 'Awarded for habits that survived a full year rather than a good January.',
 'Complete 600 habit ticks this year.',
 'YEAR', 'HABITS_COMPLETED', 600, 2500, 'check', 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000007', 'YEAR_DISTANCE', 'Long Road',
 'Awarded for the distance a year of running adds up to.',
 'Run 400 km in total this year.',
 'YEAR', 'RUN_DISTANCE_METERS', 400000, 2500, 'footprints', 130, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000008', 'YEAR_FINISHER', 'Finisher',
 'Awarded for setting goals across a year and actually closing them.',
 'Complete 12 goals this year.',
 'YEAR', 'GOALS_COMPLETED', 12, 2000, 'target', 140, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('0b1f4a10-0001-4000-8000-000000000009', 'YEAR_HAUL', 'Forged',
 'The year badge. Awarded for a year of work at a scale that speaks for itself.',
 'Earn 30,000 points this year.',
 'YEAR', 'TOTAL_POINTS', 30000, 5000, 'flame', 150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
