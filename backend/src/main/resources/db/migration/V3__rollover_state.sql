-- Tracks, per user, the last local date the hourly rollover job has fully processed
-- (spec §5.6). This lives in the points module's own migration because rollover exists
-- to settle points for a closed day — even though no rule currently fires here (habits
-- arrive at M3), the job's idempotency guarantee depends on this table existing now.

CREATE TABLE user_rollover_state (
    user_id               UUID NOT NULL,
    last_processed_date   DATE,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_rollover_state PRIMARY KEY (user_id),
    CONSTRAINT fk_user_rollover_state_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
