-- Forge 66: a 66-day self-improvement program, built from a short questionnaire about
-- the user's life (owner request).
--
-- It is deliberately its own world. Quests are generated from the questionnaire's
-- answers and never read habits, workouts or runs — the owner asked for the two not to
-- be mixed. The one bridge back to the rest of the app is points: completing a quest
-- pays into the main score through PointsService like any other earning. Everything
-- else — XP, levels, stats, streaks, Hard Mode penalties — is computed from
-- forge_quest on read and never touches the points ledger.
--
-- Why there is no XP ledger: every XP number is a pure function of which quests were
-- completed on which days. Storing it as well would only create a second copy that can
-- drift, which is exactly what the points ledger's reconciliation exists to fight.

-- Quest completions pay points under their own category, so the Home breakdown shows
-- them as what they are rather than folding them into a neighbour.
ALTER TABLE points_entry DROP CONSTRAINT ck_points_entry_category;
ALTER TABLE points_entry ADD CONSTRAINT ck_points_entry_category CHECK (category IN
    ('WORKOUT', 'RUN', 'HABIT', 'ACTIVITY', 'GOAL', 'JOB', 'REWARD', 'BADGE', 'FORGE', 'ADJUSTMENT'));

CREATE TABLE forge_program (
    id              UUID          NOT NULL,
    user_id         UUID          NOT NULL,
    start_date      DATE          NOT NULL,
    end_date        DATE          NOT NULL,
    mode            VARCHAR(10)   NOT NULL,
    status          VARCHAR(12)   NOT NULL,
    -- The areas the questionnaire picked, strongest need first, comma-separated.
    focus_areas     VARCHAR(100)  NOT NULL,
    -- The raw answers, kept so the result can be explained and the quiz retaken later.
    answers_json    VARCHAR(2000) NOT NULL,
    -- The first day that no longer belongs to an abandoned program.
    ended_on        DATE,
    -- Equal to user_id while the program is ACTIVE, null otherwise. A plain unique
    -- constraint on it allows any number of finished programs but only one running one,
    -- on both H2 and PostgreSQL, without a partial index (which H2 does not have).
    active_user_id  UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_forge_program PRIMARY KEY (id),
    CONSTRAINT fk_forge_program_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_forge_program_active UNIQUE (active_user_id),
    CONSTRAINT ck_forge_program_mode CHECK (mode IN ('NORMAL', 'HARD')),
    CONSTRAINT ck_forge_program_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_forge_program_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_forge_program_user ON forge_program (user_id, start_date);

-- The quest catalogue. Seeded here rather than in Java so rebalancing XP or rewording a
-- quest is a migration, like every other tunable number in the app.
CREATE TABLE forge_quest_template (
    id           UUID          NOT NULL,
    code         VARCHAR(40)   NOT NULL,
    area         VARCHAR(12)   NOT NULL,
    -- 1 = foundation (days 1-22), 2 = build (23-44), 3 = forge (45-66).
    tier         INTEGER       NOT NULL,
    title        VARCHAR(80)   NOT NULL,
    description  VARCHAR(300)  NOT NULL,
    base_xp      INTEGER       NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_forge_quest_template PRIMARY KEY (id),
    CONSTRAINT uq_forge_quest_template_code UNIQUE (code),
    CONSTRAINT ck_forge_quest_template_area CHECK (area IN
        ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL')),
    CONSTRAINT ck_forge_quest_template_tier CHECK (tier BETWEEN 1 AND 3),
    CONSTRAINT ck_forge_quest_template_xp CHECK (base_xp > 0)
);

-- One row per quest per day, generated for all 66 days when the program starts.
CREATE TABLE forge_quest (
    id             UUID         NOT NULL,
    program_id     UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    quest_date     DATE         NOT NULL,
    day_number     INTEGER      NOT NULL,
    template_code  VARCHAR(40)  NOT NULL,
    -- Fixed when the plan is generated (Hard Mode's multiplier included), so a later
    -- rebalance of the catalogue never rewrites what a past day was worth.
    xp             INTEGER      NOT NULL,
    -- Null until the user marks it done. Only today's quests can be marked or unmarked.
    completed_at   TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_forge_quest PRIMARY KEY (id),
    CONSTRAINT fk_forge_quest_program FOREIGN KEY (program_id) REFERENCES forge_program (id) ON DELETE CASCADE,
    CONSTRAINT fk_forge_quest_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_forge_quest_template FOREIGN KEY (template_code) REFERENCES forge_quest_template (code),
    CONSTRAINT uq_forge_quest_day UNIQUE (program_id, quest_date, template_code),
    CONSTRAINT ck_forge_quest_day_number CHECK (day_number >= 1),
    CONSTRAINT ck_forge_quest_xp CHECK (xp > 0)
);

CREATE INDEX ix_forge_quest_user_date ON forge_quest (user_id, quest_date);
CREATE INDEX ix_forge_quest_program ON forge_quest (program_id, quest_date);

-- The points a completed quest pays into the main score: round(quest XP * pointsPerXp).
-- Nothing else in Forge 66 ever writes points.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000a0', NULL, 'FORGE_QUEST',
 '{"pointsPerXp":0.5}', TRUE,
 'Paid into the main score when a Forge 66 quest is completed.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- XP, not points: kept here anyway so every tunable number in the app has one home.
-- Level n -> n+1 costs round(levelBase * n^levelExponent) XP.
('0b1f4a10-0000-4000-8000-0000000000a1', NULL, 'FORGE_PROGRESSION',
 '{"lengthDays":66,"questsPerPhase":[3,4,5],"focusAreas":3,"levelBase":100,"levelExponent":1.5,"hardModeXpMultiplier":1.2,"dayCompleteXp":25,"hardModePenaltyXp":10,"streakBonusXp":{"7":50,"21":150,"66":500}}',
 TRUE,
 'Forge 66 XP and plan shape. XP only — never paid into the points ledger.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- The starting catalogue: two quests per area per tier. Tier 1 is small enough to do on
-- a bad day; tier 3 is a real stretch. Wording is original to DailyForge.
INSERT INTO forge_quest_template (id, code, area, tier, title, description, base_xp, created_at, updated_at) VALUES

-- Fitness
('0b1f4a10-0066-4000-8000-000000000101', 'WALK_20', 'FITNESS', 1, 'Walk for 20 minutes',
 'Any pace, anywhere. Outside if you can.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000102', 'STRETCH_10', 'FITNESS', 1, 'Stretch for 10 minutes',
 'Hips, hamstrings, shoulders. Slow and easy, no bouncing.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000103', 'PUSHUPS_50', 'FITNESS', 2, '50 push-ups through the day',
 'Split them however you like. Knees are fine — the count is what matters.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000104', 'STEPS_8K', 'FITNESS', 2, 'Reach 8,000 steps',
 'Take the long way, the stairs, the walk after dinner.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000105', 'TRAIN_45', 'FITNESS', 3, 'Train hard for 45 minutes',
 'A full session — weights, a class, a hard run. Finish tired.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000106', 'CARDIO_30', 'FITNESS', 3, '30 minutes of hard cardio',
 'Run, cycle, swim or row at a pace where talking is difficult.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Focus
('0b1f4a10-0066-4000-8000-000000000201', 'TOP_THREE', 'FOCUS', 1, 'Write tomorrow''s top three',
 'Three things that would make tomorrow count. Paper is fine.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000202', 'PHONE_FREE_WAKE', 'FOCUS', 1, 'No phone for the first 30 minutes',
 'Wake up before the feed does.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000203', 'DEEP_WORK_60', 'FOCUS', 2, 'One hour of deep work',
 'One task, phone in another room, one tab open.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000204', 'QUIET_MORNING', 'FOCUS', 2, 'Notifications off until noon',
 'Do-not-disturb on from waking until midday. Check messages on purpose, not on reflex.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000205', 'DEEP_WORK_2X90', 'FOCUS', 3, 'Two 90-minute deep-work blocks',
 'Two long stretches of single-task work, with a real break between them.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000206', 'SCREEN_CAP_60', 'FOCUS', 3, 'Under one hour of leisure screens',
 'Scrolling, streaming and games together, all day. Work does not count.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Health
('0b1f4a10-0066-4000-8000-000000000301', 'WATER_2L', 'HEALTH', 1, 'Drink 2 litres of water',
 'Keep a bottle in sight. Most of it before evening.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000302', 'LIGHTS_OUT', 'HEALTH', 1, 'Lights out on time',
 'Pick a bedtime that leaves room for 7–8 hours, and keep it tonight.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000303', 'NO_ADDED_SUGAR', 'HEALTH', 2, 'No added sugar today',
 'No sweets, sugary drinks or desserts. Fruit is fine.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000304', 'VEG_THREE', 'HEALTH', 2, 'Three servings of vegetables',
 'Spread across the day. A handful counts as one.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000305', 'COLD_FINISH', 'HEALTH', 3, 'End your shower with 60 seconds cold',
 'Full cold for a full minute. Breathe slowly through it.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000306', 'HOME_COOKED', 'HEALTH', 3, 'Only home-cooked food today',
 'Every meal made by you or someone at home. No takeaway, no packets.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Mindset
('0b1f4a10-0066-4000-8000-000000000401', 'THREE_GOOD', 'MINDSET', 1, 'Write down three good things',
 'Three things from today that went well, however small.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000402', 'BREATHE_5', 'MINDSET', 1, 'Five minutes of slow breathing',
 'In for four, out for six. Sit somewhere quiet.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000403', 'MEDITATE_15', 'MINDSET', 2, 'Meditate for 15 minutes',
 'Eyes closed, attention on the breath. Wandering is normal — just come back.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000404', 'JOURNAL_PAGE', 'MINDSET', 2, 'Fill one journal page',
 'Write without editing: what is on your mind, what you want, what is in the way.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000405', 'AVOIDED_TASK', 'MINDSET', 3, 'Do the thing you have been avoiding',
 'The call, the form, the conversation. Do it first, before anything easier.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000406', 'NO_COMPLAINING', 'MINDSET', 3, 'A full day without complaining',
 'Out loud or in messages. Catch it, and say what you would do about it instead.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Learning
('0b1f4a10-0066-4000-8000-000000000501', 'READ_10', 'LEARNING', 1, 'Read 10 pages',
 'A real book, on paper or a screen. Not articles or threads.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000502', 'ONE_NEW_THING', 'LEARNING', 1, 'Learn one new thing and note it',
 'A fact, a word, a technique. Write one line about it.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000503', 'READ_30', 'LEARNING', 2, 'Read for 30 minutes',
 'One sitting, no phone nearby.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000504', 'SKILL_30', 'LEARNING', 2, '30 minutes on a skill or course',
 'Something you want to be better at in a year. Practise it, do not just watch it.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000505', 'TEACH_IT', 'LEARNING', 3, 'Write up what you learned',
 'Explain one idea from this week in a page, as if teaching someone new to it.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000506', 'SKILL_60', 'LEARNING', 3, 'One hour of focused practice',
 'Work at the edge of what you can do, not what you already can.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- Social
('0b1f4a10-0066-4000-8000-000000000601', 'REACH_OUT', 'SOCIAL', 1, 'Message someone you have lost touch with',
 'No reason needed. "Thought of you today" is enough.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000602', 'COMPLIMENT', 'SOCIAL', 1, 'Give one genuine compliment',
 'Specific and true, to someone who will not expect it.', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000603', 'CALL_20', 'SOCIAL', 2, 'A 20-minute call with family or a friend',
 'A voice call, not texts. Ask about them before talking about you.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000604', 'PHONES_AWAY_MEAL', 'SOCIAL', 2, 'One meal with people, phones away',
 'Face down and out of reach for the whole meal.', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000605', 'MEET_UP', 'SOCIAL', 3, 'Meet someone in person',
 'Make the plan and keep it — coffee, a walk, a meal.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0b1f4a10-0066-4000-8000-000000000606', 'HELP_UNASKED', 'SOCIAL', 3, 'Help someone, unasked',
 'Notice what someone needs and do it before they have to ask.', 35, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
