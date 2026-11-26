-- Carbs, fiber and sugar join protein, calories and fat. Grams, per food and per entry.
ALTER TABLE protein_food ADD COLUMN carbs_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_food ADD COLUMN fiber_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_food ADD COLUMN sugar_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_food ADD CONSTRAINT ck_protein_food_more CHECK (
    carbs_g BETWEEN 0 AND 500 AND fiber_g BETWEEN 0 AND 100 AND sugar_g BETWEEN 0 AND 300);

ALTER TABLE protein_log ADD COLUMN carbs_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_log ADD COLUMN fiber_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_log ADD COLUMN sugar_g INTEGER NOT NULL DEFAULT 0;
ALTER TABLE protein_log ADD CONSTRAINT ck_protein_log_more CHECK (
    carbs_g BETWEEN 0 AND 500 AND fiber_g BETWEEN 0 AND 100 AND sugar_g BETWEEN 0 AND 300);

-- Carbs and fiber are targets to reach; sugar is a limit to stay under.
ALTER TABLE nutrition_settings ADD COLUMN carbs_target_g INTEGER NOT NULL DEFAULT 250;
ALTER TABLE nutrition_settings ADD COLUMN fiber_target_g INTEGER NOT NULL DEFAULT 30;
ALTER TABLE nutrition_settings ADD COLUMN sugar_limit_g INTEGER NOT NULL DEFAULT 50;
ALTER TABLE nutrition_settings ADD CONSTRAINT ck_nutrition_settings_more CHECK (
    carbs_target_g BETWEEN 10 AND 800 AND fiber_target_g BETWEEN 5 AND 100 AND sugar_limit_g BETWEEN 5 AND 300);

-- Which starter list a user has been given. 1 = the first twelve foods; 2 adds common
-- Indian dishes. A user on an older list gets only the newer foods, once, by name.
ALTER TABLE nutrition_settings ADD COLUMN starter_version INTEGER NOT NULL DEFAULT 1;

-- Typical values for the starter foods users already have; editable in Settings.
UPDATE protein_food SET carbs_g = 1,  fiber_g = 0,  sugar_g = 1  WHERE name = '2 eggs' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 3,  fiber_g = 0,  sugar_g = 3  WHERE name = 'Paneer 100 g' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 9,  fiber_g = 0,  sugar_g = 9  WHERE name = 'Curd 200 g' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 6,  fiber_g = 0,  sugar_g = 5  WHERE name = 'Greek yogurt 150 g' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 17, fiber_g = 7,  sugar_g = 4  WHERE name = 'Soya chunks 50 g' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 25, fiber_g = 6,  sugar_g = 1  WHERE name = 'Dal bowl' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 3,  fiber_g = 0,  sugar_g = 2  WHERE name = 'Whey scoop' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 12, fiber_g = 0,  sugar_g = 12 WHERE name = 'Milk 250 ml' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 45, fiber_g = 12, sugar_g = 8  WHERE name = 'Chana 1 cup' AND carbs_g = 0;
UPDATE protein_food SET carbs_g = 3,  fiber_g = 2,  sugar_g = 1  WHERE name = 'Tofu 100 g' AND carbs_g = 0;
