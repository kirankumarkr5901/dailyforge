-- Tape measurements alongside the weigh-in, all optional, in centimetres. On a cut the
-- waist often moves before the scale does.
ALTER TABLE body_metric ADD COLUMN waist_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD COLUMN chest_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD COLUMN arm_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD COLUMN hips_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD COLUMN thigh_cm NUMERIC(5, 1);
ALTER TABLE body_metric ADD CONSTRAINT ck_body_metric_measurements CHECK (
    (waist_cm IS NULL OR waist_cm BETWEEN 20 AND 250)
    AND (chest_cm IS NULL OR chest_cm BETWEEN 20 AND 250)
    AND (arm_cm IS NULL OR arm_cm BETWEEN 10 AND 100)
    AND (hips_cm IS NULL OR hips_cm BETWEEN 20 AND 250)
    AND (thigh_cm IS NULL OR thigh_cm BETWEEN 10 AND 150));
