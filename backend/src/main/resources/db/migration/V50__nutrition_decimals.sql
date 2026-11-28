-- Nutrition keeps one decimal (owner request): protein, fat, carbs, fiber and sugar in
-- grams as NUMERIC(6,1). Calories stay whole. The checks are dropped and added back
-- around the type change, since a column a check refers to can't change type on H2.
ALTER TABLE protein_food DROP CONSTRAINT ck_protein_food_amounts;
ALTER TABLE protein_food DROP CONSTRAINT ck_protein_food_more;
ALTER TABLE protein_log DROP CONSTRAINT ck_protein_log_amounts;
ALTER TABLE protein_log DROP CONSTRAINT ck_protein_log_more;

ALTER TABLE protein_food ALTER COLUMN grams SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_food ALTER COLUMN fat_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_food ALTER COLUMN carbs_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_food ALTER COLUMN fiber_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_food ALTER COLUMN sugar_g SET DATA TYPE NUMERIC(6, 1);

ALTER TABLE protein_log ALTER COLUMN grams SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_log ALTER COLUMN fat_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_log ALTER COLUMN carbs_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_log ALTER COLUMN fiber_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE protein_log ALTER COLUMN sugar_g SET DATA TYPE NUMERIC(6, 1);

ALTER TABLE meal_item ALTER COLUMN grams SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE meal_item ALTER COLUMN fat_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE meal_item ALTER COLUMN carbs_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE meal_item ALTER COLUMN fiber_g SET DATA TYPE NUMERIC(6, 1);
ALTER TABLE meal_item ALTER COLUMN sugar_g SET DATA TYPE NUMERIC(6, 1);

ALTER TABLE protein_food ADD CONSTRAINT ck_protein_food_amounts CHECK (
    grams BETWEEN 0 AND 200 AND kcal BETWEEN 0 AND 3000 AND fat_g BETWEEN 0 AND 300
    AND (grams > 0 OR kcal > 0 OR fat_g > 0));
ALTER TABLE protein_food ADD CONSTRAINT ck_protein_food_more CHECK (
    carbs_g BETWEEN 0 AND 500 AND fiber_g BETWEEN 0 AND 100 AND sugar_g BETWEEN 0 AND 300);
ALTER TABLE protein_log ADD CONSTRAINT ck_protein_log_amounts CHECK (
    grams BETWEEN 0 AND 200 AND kcal BETWEEN 0 AND 3000 AND fat_g BETWEEN 0 AND 300
    AND (grams > 0 OR kcal > 0 OR fat_g > 0));
ALTER TABLE protein_log ADD CONSTRAINT ck_protein_log_more CHECK (
    carbs_g BETWEEN 0 AND 500 AND fiber_g BETWEEN 0 AND 100 AND sugar_g BETWEEN 0 AND 300);
