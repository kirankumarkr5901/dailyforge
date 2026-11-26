-- Features a user has switched off for now (comma-separated codes, e.g. "NUTRITION,GOALS").
-- Switched-off features are hidden and their automatic points pause; their data stays.
ALTER TABLE user_settings ADD COLUMN disabled_features VARCHAR(200) NOT NULL DEFAULT '';
