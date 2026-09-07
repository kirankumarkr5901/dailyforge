-- Rewards in three tiers (owner feedback): Micro (a small daily treat), Weekly, and
-- Monthly — grouping the list so a 5-point treat and a 2000-point splurge do not sit in
-- the same undifferentiated pile. Every reward that already exists defaults to MICRO,
-- the least assumption to make about something created before tiers existed.
ALTER TABLE reward ADD COLUMN tier VARCHAR(10) NOT NULL DEFAULT 'MICRO';
ALTER TABLE reward ADD CONSTRAINT ck_reward_tier CHECK (tier IN ('MICRO', 'WEEKLY', 'MONTHLY'));
