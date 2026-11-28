-- The AI goal planner: a goal described in words becomes a goal with milestones, habits
-- to start, a custom skill path and workout additions; each week the AI may propose new
-- milestone dates for the user to accept.

-- Steps on the way to a goal, each with its own date.
CREATE TABLE goal_milestone (
    id          UUID          NOT NULL,
    goal_id     UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    due_date    DATE          NOT NULL,
    done_on     DATE,
    sort_order  INTEGER       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_goal_milestone PRIMARY KEY (id),
    CONSTRAINT fk_goal_milestone_goal FOREIGN KEY (goal_id) REFERENCES goal (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_milestone_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_goal_milestone_goal ON goal_milestone (goal_id, sort_order);

-- A goal the AI planned: what the user asked for, and the plan's summary, for later reviews.
CREATE TABLE goal_ai_plan (
    goal_id     UUID           NOT NULL,
    user_id     UUID           NOT NULL,
    brief       VARCHAR(1500)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_goal_ai_plan PRIMARY KEY (goal_id),
    CONSTRAINT fk_goal_ai_plan_goal FOREIGN KEY (goal_id) REFERENCES goal (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_ai_plan_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

-- The weekly re-tune: a note and proposed milestones, waiting for the user's answer.
CREATE TABLE goal_ai_review (
    goal_id     UUID           NOT NULL,
    week_start  DATE           NOT NULL,
    note        VARCHAR(600)   NOT NULL,
    proposal    VARCHAR(4000)  NOT NULL,
    status      VARCHAR(10)    NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_goal_ai_review PRIMARY KEY (goal_id, week_start),
    CONSTRAINT fk_goal_ai_review_goal FOREIGN KEY (goal_id) REFERENCES goal (id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_ai_review_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DISMISSED', 'NONE'))
);

-- Skills the AI wrote for a user, next to the three built-in ones. Three levels, like
-- them; level and done days use the same skill_progress and skill_log tables.
CREATE TABLE custom_skill (
    id          UUID           NOT NULL,
    user_id     UUID           NOT NULL,
    code        VARCHAR(20)    NOT NULL,
    name        VARCHAR(60)    NOT NULL,
    when_text   VARCHAR(120)   NOT NULL,
    safety      VARCHAR(300),
    levels_json VARCHAR(6000)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_custom_skill PRIMARY KEY (id),
    CONSTRAINT fk_custom_skill_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_custom_skill_code UNIQUE (user_id, code)
);
