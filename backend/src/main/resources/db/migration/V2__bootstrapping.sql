-- Bootstrapping (UC-02, US-013; docs/data-model.puml). Runs as oea_owner.
-- Subset of the modeled tables; columns referencing persons follow with the persons table.

CREATE TABLE system_admin_accounts (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    login_name              TEXT        NOT NULL UNIQUE CHECK (login_name ~ '^[a-z][a-z0-9._-]{2,63}$'),
    status                  TEXT        NOT NULL DEFAULT 'bootstrapped'
                                        CHECK (status IN ('bootstrapped', 'active', 'deactivated')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    first_login_at          TIMESTAMPTZ,
    last_used_at            TIMESTAMPTZ,
    deactivated_at          TIMESTAMPTZ,
    deactivated_by_admin_id UUID        REFERENCES system_admin_accounts (id)
);

CREATE TABLE system_settings (
    id                                     SMALLINT    PRIMARY KEY CHECK (id = 1),
    -- Instance (ADR-001): set by the bootstrapping, immutable afterwards
    instance_slug                          TEXT        NOT NULL
                                                       CHECK (instance_slug ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$'),
    -- Authorization / sessions (ADR-032)
    privileged_assignment_approval         BOOLEAN     NOT NULL DEFAULT false,
    allow_reactivation_of_offboarded       BOOLEAN     NOT NULL DEFAULT false,
    session_retention_days                 INTEGER     NOT NULL DEFAULT 30 CHECK (session_retention_days > 0),
    -- Raw analytics (ADR-034)
    analytics_enabled                      BOOLEAN     NOT NULL DEFAULT false,
    analytics_statement_timeout_seconds    INTEGER     NOT NULL DEFAULT 30
                                                       CHECK (analytics_statement_timeout_seconds BETWEEN 1 AND 120),
    analytics_max_rows                     INTEGER     NOT NULL DEFAULT 10000
                                                       CHECK (analytics_max_rows BETWEEN 1 AND 100000),
    analytics_max_result_bytes             INTEGER     NOT NULL DEFAULT 10485760 CHECK (analytics_max_result_bytes > 0),
    analytics_max_concurrent_per_principal INTEGER     NOT NULL DEFAULT 2
                                                       CHECK (analytics_max_concurrent_per_principal > 0),
    analytics_queries_per_minute           INTEGER     NOT NULL DEFAULT 30 CHECK (analytics_queries_per_minute > 0),
    analytics_pool_size                    INTEGER     NOT NULL DEFAULT 4 CHECK (analytics_pool_size > 0),
    -- Change tracking
    updated_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_admin_id                    UUID        REFERENCES system_admin_accounts (id)
);

CREATE FUNCTION system_settings_keep_instance_slug() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NEW.instance_slug IS DISTINCT FROM OLD.instance_slug THEN
        RAISE EXCEPTION 'instance_slug is immutable after bootstrapping (ADR-001)';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER system_settings_keep_instance_slug BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION system_settings_keep_instance_slug();

CREATE TABLE local_credentials (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    system_admin_account_id UUID        REFERENCES system_admin_accounts (id),
    type                    TEXT        NOT NULL CHECK (type IN ('passkey', 'totp', 'password', 'recovery-codes')),
    status                  TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at            TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    revoked_reason          TEXT        CHECK (revoked_reason IN
                                        ('device_lost', 'superseded', 'admin_action', 'person_request', 'factor_reset')),
    revoked_by_admin_id     UUID        REFERENCES system_admin_accounts (id),
    -- exactly one owner; extended with person_id together with the persons table
    CONSTRAINT local_credentials_owner CHECK (system_admin_account_id IS NOT NULL)
);

-- At most one active password per owner
CREATE UNIQUE INDEX local_credentials_one_active_password_per_admin
    ON local_credentials (system_admin_account_id) WHERE type = 'password' AND status = 'active';

CREATE TABLE password_credentials (
    credential_id UUID        PRIMARY KEY REFERENCES local_credentials (id),
    password_hash TEXT        NOT NULL CHECK (password_hash LIKE '$argon2id$%'),
    origin        TEXT        NOT NULL DEFAULT 'self-set'
                              CHECK (origin IN ('self-set', 'initial', 'admin-reset', 'break-glass')),
    expires_at    TIMESTAMPTZ,
    CONSTRAINT password_credentials_temporary_expiry CHECK ((origin = 'self-set') = (expires_at IS NULL))
);

CREATE TABLE bootstrap_setup_tokens (
    id         SMALLINT    PRIMARY KEY CHECK (id = 1),
    token_hash BYTEA       NOT NULL CHECK (octet_length(token_hash) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

REVOKE ALL ON system_admin_accounts, system_settings, local_credentials, password_credentials,
    bootstrap_setup_tokens FROM PUBLIC;
REVOKE ALL ON FUNCTION system_settings_keep_instance_slug() FROM PUBLIC;

-- Runtime role: no DELETE on accounts and credentials (they are deactivated or revoked)
GRANT SELECT, INSERT, UPDATE ON system_admin_accounts, system_settings, local_credentials, password_credentials
    TO oea_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON bootstrap_setup_tokens TO oea_app;
