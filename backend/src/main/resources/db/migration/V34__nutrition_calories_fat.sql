-- The protein meter becomes a nutrition meter: protein, calories and fat. The tables keep
-- their protein_* names; renaming them would buy nothing but risk.

-- Foods and logs carry calories and fat too. Protein may now be 0 (rice, oil), as long
-- as a food adds something.
ALTER TABLE protein_food ADD COLUMN kcal INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_food ADD COLUMN fat_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_food DROP CONSTRAINT ck_protein_food_grams;
ALTER TABLE protein_food ADD CONSTRAINT ck_protein_food_amounts CHECK (
    grams BETWEEN 0 AND 200 AND kcal BETWEEN 0 AND 3000 AND fat_g BETWEEN 0 AND 300
    AND (grams > 0 OR kcal > 0 OR fat_g > 0));

ALTER TABLE protein_log ADD COLUMN kcal INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_log ADD COLUMN fat_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_log DROP CONSTRAINT ck_protein_log_grams;
ALTER TABLE protein_log ADD CONSTRAINT ck_protein_log_amounts CHECK (
    grams BETWEEN 0 AND 200 AND kcal BETWEEN 0 AND 3000 AND fat_g BETWEEN 0 AND 300
    AND (grams > 0 OR kcal > 0 OR fat_g > 0));

-- A calorie budget to stay under (the Cut Block plan's 2,250 kcal) and a fat target.
ALTER TABLE nutrition_settings ADD COLUMN calorie_budget INTEGER NOT NULL DEFAULT 2250;
ALTER TABLE nutrition_settings ADD COLUMN fat_target_g INTEGER NOT NULL DEFAULT 70;
ALTER TABLE nutrition_settings ADD CONSTRAINT ck_nutrition_settings_budget CHECK (calorie_budget BETWEEN 800 AND 6000);
ALTER TABLE nutrition_settings ADD CONSTRAINT ck_nutrition_settings_fat CHECK (fat_target_g BETWEEN 10 AND 300);

-- Fill in calories and fat for the starter foods users already have, where they still
-- carry the starter name and have no calories yet. Typical values; editable in Settings.
UPDATE protein_food SET kcal = 140, fat_g = 10 WHERE name = '2 eggs' AND kcal = 0;
UPDATE protein_food SET kcal = 165, fat_g = 4  WHERE name = 'Chicken 100 g' AND kcal = 0;
UPDATE protein_food SET kcal = 265, fat_g = 21 WHERE name = 'Paneer 100 g' AND kcal = 0;
UPDATE protein_food SET kcal = 120, fat_g = 6  WHERE name = 'Curd 200 g' AND kcal = 0;
UPDATE protein_food SET kcal = 100, fat_g = 3  WHERE name = 'Greek yogurt 150 g' AND kcal = 0;
UPDATE protein_food SET kcal = 172, fat_g = 0  WHERE name = 'Soya chunks 50 g' AND kcal = 0;
UPDATE protein_food SET kcal = 180, fat_g = 5  WHERE name = 'Dal bowl' AND kcal = 0;
UPDATE protein_food SET kcal = 120, fat_g = 2  WHERE name = 'Whey scoop' AND kcal = 0;
UPDATE protein_food SET kcal = 150, fat_g = 8  WHERE name = 'Milk 250 ml' AND kcal = 0;
UPDATE protein_food SET kcal = 130, fat_g = 3  WHERE name = 'Fish 100 g' AND kcal = 0;
UPDATE protein_food SET kcal = 270, fat_g = 4  WHERE name = 'Chana 1 cup' AND kcal = 0;
UPDATE protein_food SET kcal = 145, fat_g = 9  WHERE name = 'Tofu 100 g' AND kcal = 0;

-- Paid for a closed day whose calories stayed within the budget, if anything was logged.
INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) VALUES
('0b1f4a10-0000-4000-8000-0000000000b1', NULL, 'CALORIE_BUDGET',
 '{"points":10}', TRUE,
 'Paid once a day is over, when that day''s logged calories stayed within the budget.',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
