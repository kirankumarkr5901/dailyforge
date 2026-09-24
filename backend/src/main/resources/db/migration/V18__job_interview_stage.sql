-- Interview sub-stages (owner feedback): an INTERVIEW status now also records whether
-- it is a technical or an HR round, on the application and on every event, so a
-- rejection can name the stage it came from ("Rejected after HR round 1") rather than
-- the generic "after interview".
ALTER TABLE job_application ADD COLUMN interview_stage VARCHAR(20);
ALTER TABLE job_application
    ADD CONSTRAINT ck_job_application_interview_stage CHECK (interview_stage IN ('TECHNICAL', 'HR'));

ALTER TABLE job_event ADD COLUMN interview_stage VARCHAR(20);
ALTER TABLE job_event
    ADD CONSTRAINT ck_job_event_interview_stage CHECK (interview_stage IN ('TECHNICAL', 'HR'));
