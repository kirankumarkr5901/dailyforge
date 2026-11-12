-- "Enable edit" (owner request). While it is off, the structure of the app — habits,
-- workout plans, exercises, activity types, rewards, goals, a Forge 66 program — cannot
-- be created, changed, reordered or removed. Logging effort is never affected.
--
-- Off by default, for existing accounts and new ones alike: structure is set up once
-- and then left alone, and the lock is what keeps a stray drag or tap from changing it.
ALTER TABLE user_settings ADD COLUMN editing_enabled BOOLEAN NOT NULL DEFAULT FALSE;
