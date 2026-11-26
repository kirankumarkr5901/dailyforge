-- Progress photos. The image itself lives in Cloudflare R2 under users/{user id}/...;
-- this only records which object belongs to whom, and on which day it was taken.
CREATE TABLE progress_photo (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    taken_on    DATE         NOT NULL,
    object_key  VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_progress_photo PRIMARY KEY (id),
    CONSTRAINT fk_progress_photo_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_progress_photo_key UNIQUE (object_key)
);

CREATE INDEX ix_progress_photo_user ON progress_photo (user_id, taken_on);
