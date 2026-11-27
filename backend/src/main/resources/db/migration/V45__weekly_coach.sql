-- The AI coach's review of one Monday-to-Sunday week: written once (on the Sunday, or when
-- Home first asks for it), kept so it never has to be asked for again. pushed_at marks the
-- Sunday notification as sent, so a restart never sends it twice.
CREATE TABLE weekly_coach (
    user_id     UUID    NOT NULL,
    week_start  DATE    NOT NULL,
    summary     VARCHAR(600)  NOT NULL,
    wins        VARCHAR(1000) NOT NULL,
    focus       VARCHAR(400)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    pushed_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_weekly_coach PRIMARY KEY (user_id, week_start),
    CONSTRAINT fk_weekly_coach_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
