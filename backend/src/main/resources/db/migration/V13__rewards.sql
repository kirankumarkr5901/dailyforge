-- The reward module (spec §6 "reward", §5.4 "Rewards", §8.9) — the plan's own premise is
-- "points earning and spending it on rewards"; without this the loop stays open.

CREATE TABLE reward (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    cost           INTEGER       NOT NULL,
    icon           VARCHAR(40)   NOT NULL,
    is_repeatable  BOOLEAN       NOT NULL DEFAULT TRUE,
    stock          INTEGER,
    archived_at    TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reward PRIMARY KEY (id),
    CONSTRAINT fk_reward_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_reward_cost CHECK (cost > 0),
    CONSTRAINT ck_reward_stock CHECK (stock IS NULL OR stock >= 0)
);

CREATE INDEX ix_reward_user ON reward (user_id, archived_at);

-- redeemed_at is the real-time instant; occurred_on is the logical day it counts
-- against (spec §4.2) — "undo within the same day" (spec §8.9) compares against this,
-- never against redeemed_at, for the same reason every other edit-window check in this
-- app compares logical dates rather than instants.
CREATE TABLE reward_redemption (
    id            UUID          NOT NULL,
    reward_id     UUID          NOT NULL,
    user_id       UUID          NOT NULL,
    occurred_on   DATE          NOT NULL,
    points_spent  INTEGER       NOT NULL,
    redeemed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    refunded_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_reward_redemption PRIMARY KEY (id),
    CONSTRAINT fk_reward_redemption_reward FOREIGN KEY (reward_id) REFERENCES reward (id) ON DELETE CASCADE,
    CONSTRAINT fk_reward_redemption_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_reward_redemption_user ON reward_redemption (user_id, occurred_on DESC);
CREATE INDEX ix_reward_redemption_reward ON reward_redemption (reward_id);
