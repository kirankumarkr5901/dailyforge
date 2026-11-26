-- Meal shortcuts: a named set of foods logged with one tap ("My breakfast"). Items copy
-- their values in, so editing or deleting a food later never changes a saved meal.
CREATE TABLE meal (
    id          UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    name        VARCHAR(60) NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_meal PRIMARY KEY (id),
    CONSTRAINT fk_meal_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_meal_user ON meal (user_id, sort_order);

CREATE TABLE meal_item (
    id          UUID        NOT NULL,
    meal_id     UUID        NOT NULL,
    food_name   VARCHAR(60) NOT NULL,
    grams       INTEGER     NOT NULL,
    kcal        INTEGER     NOT NULL,
    fat_g       INTEGER     NOT NULL,
    carbs_g     INTEGER     NOT NULL,
    fiber_g     INTEGER     NOT NULL,
    sugar_g     INTEGER     NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_meal_item PRIMARY KEY (id),
    CONSTRAINT fk_meal_item_meal FOREIGN KEY (meal_id) REFERENCES meal (id) ON DELETE CASCADE
);

CREATE INDEX ix_meal_item_meal ON meal_item (meal_id, sort_order);
