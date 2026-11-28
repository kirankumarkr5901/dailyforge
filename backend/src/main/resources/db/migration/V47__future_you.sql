-- "Future you": on the 1st of each month, where this pace leads in 90 days. Written once
-- per month from the user's real trends, then kept.
CREATE TABLE future_you (
    user_id     UUID          NOT NULL,
    month       DATE          NOT NULL,
    headline    VARCHAR(200)  NOT NULL,
    lines       VARCHAR(1500) NOT NULL,
    reality     VARCHAR(400)  NOT NULL,
    lever       VARCHAR(300)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_future_you PRIMARY KEY (user_id, month),
    CONSTRAINT fk_future_you_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
