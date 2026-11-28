-- Neck joins the tape measurements, so body fat can be estimated (US Navy method), and a
-- small body profile holds what that and the "right direction?" check need: which formula
-- (male or female) and what the person is aiming for.
ALTER TABLE body_metric ADD COLUMN neck_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD CONSTRAINT ck_body_metric_neck CHECK (neck_cm IS NULL OR neck_cm BETWEEN 15 AND 80);

CREATE TABLE body_profile (
    user_id     UUID         NOT NULL,
    sex         VARCHAR(6),
    goal        VARCHAR(12),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_body_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_body_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_body_profile_sex CHECK (sex IS NULL OR sex IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_body_profile_goal CHECK (goal IS NULL OR goal IN ('LOSE_FAT', 'BUILD_MUSCLE', 'RECOMP', 'MAINTAIN'))
);
