-- The job module (spec §6 "job", §8.7). Points are disabled by default (JOB_STAGE_ADVANCE,
-- seeded FALSE in V2) — job activity is trivially inflatable (spec §13.9's own reasoning).

CREATE TABLE job_application (
    id                UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    company           VARCHAR(120)  NOT NULL,
    role              VARCHAR(120)  NOT NULL,
    role_id           VARCHAR(80),
    city              VARCHAR(120),
    job_url           VARCHAR(500),
    resume_version    VARCHAR(80),
    source            VARCHAR(20)   NOT NULL,
    referrer_name     VARCHAR(120),
    status            VARCHAR(20)   NOT NULL,
    current_round     INTEGER       NOT NULL DEFAULT 0,
    next_follow_up_on DATE,
    note              VARCHAR(1000),
    applied_on        DATE          NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_job_application PRIMARY KEY (id),
    CONSTRAINT fk_job_application_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_application_source CHECK (source IN ('APPLIED', 'REFERRAL_REQUESTED', 'REFERRED', 'RECRUITER')),
    CONSTRAINT ck_job_application_status
        CHECK (status IN ('APPLIED', 'ASSESSMENT', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN', 'GHOSTED'))
);

CREATE INDEX ix_job_application_user_status ON job_application (user_id, status);
CREATE INDEX ix_job_application_followup ON job_application (user_id, next_follow_up_on);

-- Full transition history, so the pipeline is auditable and the metrics are computable
-- (spec §8.7) — never overwritten, only appended to.
CREATE TABLE job_event (
    id             UUID         NOT NULL,
    application_id UUID         NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)  NOT NULL,
    round_number   INTEGER,
    occurred_on    DATE         NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_job_event PRIMARY KEY (id),
    CONSTRAINT fk_job_event_application FOREIGN KEY (application_id) REFERENCES job_application (id) ON DELETE CASCADE
);

CREATE INDEX ix_job_event_application ON job_event (application_id, occurred_on);
