-- DailyForge baseline schema.
--
-- Portability rule (spec §3.2): these files run unchanged on H2 (MODE=PostgreSQL) and on
-- PostgreSQL 16. That rules out a few PostgreSQL niceties, and each omission is deliberate:
--   * no citext          -- emails are normalised to lower case by the application instead
--   * no native enums    -- varchar plus a CHECK constraint, which both engines agree on
--   * no jsonb           -- JSON payloads are stored as text; a later PostgreSQL-only
--                           migration may retype them once H2 is out of the picture
--   * no partial indexes -- added in the production-only migration when they are needed

-- ---------------------------------------------------------------------------
-- identity
-- ---------------------------------------------------------------------------

CREATE TABLE app_user (
    id               UUID         NOT NULL,
    email            VARCHAR(320) NOT NULL,
    password_hash    VARCHAR(100),
    google_sub       VARCHAR(255),
    display_name     VARCHAR(80)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_google_sub UNIQUE (google_sub),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    -- A user signs in with a password, with Google, or with both. Never with neither.
    CONSTRAINT ck_app_user_has_credential CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
);

CREATE TABLE user_settings (
    user_id                 UUID        NOT NULL,
    time_zone               VARCHAR(64) NOT NULL DEFAULT 'UTC',
    unit_system             VARCHAR(10) NOT NULL DEFAULT 'METRIC',
    theme                   VARCHAR(10) NOT NULL DEFAULT 'SYSTEM',
    week_start              VARCHAR(10) NOT NULL DEFAULT 'MONDAY',
    commitment_bonus        INTEGER     NOT NULL DEFAULT 0,
    height_cm               NUMERIC(5, 1),
    reminder_time           TIME,
    onboarding_completed_at TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_settings_unit_system CHECK (unit_system IN ('METRIC', 'IMPERIAL')),
    CONSTRAINT ck_user_settings_theme CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    CONSTRAINT ck_user_settings_commitment_bonus CHECK (commitment_bonus >= 0)
);

CREATE TABLE refresh_token (
    id           UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(255) NOT NULL,
    device_label VARCHAR(120),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id, expires_at);

-- ---------------------------------------------------------------------------
-- points  --  the spine (spec §5)
-- ---------------------------------------------------------------------------

-- Append-only. Nothing in the application may UPDATE amount or DELETE a row.
-- An undo writes a second row pointing at the first through reverses_id.
CREATE TABLE points_entry (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    occurred_on     DATE         NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    rule_code       VARCHAR(60)  NOT NULL,
    amount          INTEGER      NOT NULL,
    source_type     VARCHAR(40),
    source_id       UUID,
    reverses_id     UUID,
    reversed        BOOLEAN      NOT NULL DEFAULT FALSE,
    description     VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_points_entry PRIMARY KEY (id),
    CONSTRAINT fk_points_entry_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_points_entry_reverses FOREIGN KEY (reverses_id) REFERENCES points_entry (id),
    CONSTRAINT uq_points_entry_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_points_entry_category CHECK (category IN
        ('WORKOUT', 'RUN', 'HABIT', 'ACTIVITY', 'GOAL', 'JOB', 'REWARD', 'ADJUSTMENT')),
    -- A compensating entry cannot itself be the thing it compensates.
    CONSTRAINT ck_points_entry_not_self_reversing CHECK (reverses_id IS NULL OR reverses_id <> id)
);

CREATE INDEX ix_points_entry_user_date ON points_entry (user_id, occurred_on);
CREATE INDEX ix_points_entry_user_category_date ON points_entry (user_id, category, occurred_on);
CREATE INDEX ix_points_entry_source ON points_entry (source_type, source_id);
CREATE INDEX ix_points_entry_reverses ON points_entry (reverses_id);

-- Every point value in the app comes from here. No magic numbers in code (spec §5.4).
-- user_id NULL is the system default; a row with a user_id overrides it for that user.
CREATE TABLE points_rule_config (
    id           UUID         NOT NULL,
    user_id      UUID,
    rule_code    VARCHAR(60)  NOT NULL,
    config_json  VARCHAR(4000) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    description  VARCHAR(200) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_points_rule_config PRIMARY KEY (id),
    CONSTRAINT fk_points_rule_config_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

-- One unique index per shape, because NULL user_id would defeat a single composite unique.
CREATE UNIQUE INDEX uq_points_rule_config_user ON points_rule_config (user_id, rule_code);

-- A cache, never the source of truth. Rebuildable from points_entry at any time.
CREATE TABLE user_score_cache (
    user_id       UUID    NOT NULL,
    total_points  INTEGER NOT NULL DEFAULT 0,
    entry_count   INTEGER NOT NULL DEFAULT 0,
    recalculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_user_score_cache PRIMARY KEY (user_id),
    CONSTRAINT fk_user_score_cache_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
