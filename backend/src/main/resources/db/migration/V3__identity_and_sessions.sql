-- Identity, authorization and sessions (change us-013-system-admin-login; docs/data-model.puml).
-- Runs as oea_owner. Columns referencing entities follow with the entities table.

CREATE TABLE persons (
    id                          BIGSERIAL   PRIMARY KEY,
    person_type                 TEXT        NOT NULL DEFAULT 'human' CHECK (person_type IN ('human', 'service-account')),
    name                        TEXT        NOT NULL,
    email                       TEXT        UNIQUE,
    employee_id                 TEXT,
    status                      TEXT        NOT NULL DEFAULT 'invited'
                                            CHECK (status IN ('invited', 'active', 'inactive', 'offboarded')),
    status_changed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    status_changed_by_person_id BIGINT      REFERENCES persons (id),
    status_changed_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    start_date                  DATE,
    end_date                    DATE,
    detail_level                TEXT        NOT NULL DEFAULT 'reference-only'
                                            CHECK (detail_level IN ('full', 'reference-only', 'anonymized')),
    external_ref_system         TEXT,
    external_ref_id             TEXT,
    ui_language                 TEXT        NOT NULL DEFAULT 'de',
    ui_theme                    TEXT        NOT NULL DEFAULT 'system',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT persons_service_accounts_never_invited CHECK (person_type = 'human' OR status <> 'invited')
);

CREATE TABLE auth_providers (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_type           TEXT        NOT NULL DEFAULT 'generic-oidc'
                                        CHECK (provider_type IN ('entra-id', 'authentik', 'generic-oidc')),
    name                    TEXT        NOT NULL,
    issuer                  TEXT        NOT NULL UNIQUE,
    client_id               TEXT        NOT NULL,
    client_secret_encrypted TEXT,
    client_secret_key_id    TEXT,
    scopes                  TEXT        NOT NULL DEFAULT 'openid profile email',
    active                  BOOLEAN     NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    CONSTRAINT auth_providers_secret_key CHECK ((client_secret_encrypted IS NULL) = (client_secret_key_id IS NULL))
);

CREATE TABLE person_identities (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id              BIGINT      NOT NULL REFERENCES persons (id),
    auth_provider_id       UUID        NOT NULL REFERENCES auth_providers (id),
    issuer                 TEXT        NOT NULL,
    subject                TEXT        NOT NULL,
    link_method            TEXT        NOT NULL CHECK (link_method IN ('admin-entered', 'email-match')),
    linked_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at           TIMESTAMPTZ,
    confirmed_by_person_id BIGINT      REFERENCES persons (id),
    confirmed_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    last_login_at          TIMESTAMPTZ,
    CONSTRAINT person_identities_login_key UNIQUE (issuer, subject)
);

CREATE TABLE permissions (
    id          TEXT    PRIMARY KEY,
    category    TEXT    NOT NULL,
    admin_class BOOLEAN NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE authorization_roles (
    id                             TEXT        PRIMARY KEY,
    name                           TEXT        NOT NULL,
    description                    TEXT,
    is_builtin                     BOOLEAN     NOT NULL DEFAULT false,
    assignable_to_service_accounts BOOLEAN     NOT NULL DEFAULT false,
    overrides_property_visibility  BOOLEAN     NOT NULL DEFAULT false,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_person_id           BIGINT      REFERENCES persons (id),
    created_by_admin_id            UUID        REFERENCES system_admin_accounts (id),
    updated_at                     TIMESTAMPTZ,
    updated_by_person_id           BIGINT      REFERENCES persons (id),
    updated_by_admin_id            UUID        REFERENCES system_admin_accounts (id),
    deleted_at                     TIMESTAMPTZ,
    deleted_by_person_id           BIGINT      REFERENCES persons (id),
    deleted_by_admin_id            UUID        REFERENCES system_admin_accounts (id)
);

CREATE TABLE role_permissions (
    role_id              TEXT        NOT NULL REFERENCES authorization_roles (id),
    permission_id        TEXT        NOT NULL REFERENCES permissions (id),
    added_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    added_by_person_id   BIGINT      REFERENCES persons (id),
    added_by_admin_id    UUID        REFERENCES system_admin_accounts (id),
    approval_status      TEXT        NOT NULL DEFAULT 'approved' CHECK (approval_status IN ('pending', 'approved', 'rejected')),
    decided_at           TIMESTAMPTZ,
    decided_by_person_id BIGINT      REFERENCES persons (id),
    decided_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE idp_group_mappings (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_provider_id     UUID        NOT NULL REFERENCES auth_providers (id),
    issuer               TEXT        NOT NULL,
    claim_type           TEXT        NOT NULL DEFAULT 'group' CHECK (claim_type IN ('group', 'app-role')),
    group_identifier     TEXT        NOT NULL,
    display_label        TEXT,
    role_id              TEXT        NOT NULL REFERENCES authorization_roles (id),
    active               BOOLEAN     NOT NULL DEFAULT true,
    approval_status      TEXT        NOT NULL DEFAULT 'approved' CHECK (approval_status IN ('pending', 'approved', 'rejected')),
    decided_at           TIMESTAMPTZ,
    decided_by_person_id BIGINT      REFERENCES persons (id),
    decided_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_person_id BIGINT      REFERENCES persons (id),
    created_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    updated_at           TIMESTAMPTZ,
    updated_by_person_id BIGINT      REFERENCES persons (id),
    updated_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    deleted_at           TIMESTAMPTZ,
    deleted_by_person_id BIGINT      REFERENCES persons (id),
    deleted_by_admin_id  UUID        REFERENCES system_admin_accounts (id)
);

-- One live mapping per issuer, group and role
CREATE UNIQUE INDEX idp_group_mappings_live ON idp_group_mappings (issuer, group_identifier, role_id)
    WHERE deleted_at IS NULL;

CREATE TABLE sessions (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash                  BYTEA       NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    person_id                   BIGINT      REFERENCES persons (id),
    system_admin_account_id     UUID        REFERENCES system_admin_accounts (id),
    client_type                 TEXT        NOT NULL CHECK (client_type IN ('web-portal', 'electron')),
    auth_method                 TEXT        NOT NULL CHECK (auth_method IN ('oidc', 'passkey', 'password-totp', 'password')),
    auth_provider_id            UUID        REFERENCES auth_providers (id),
    second_factor_verified      BOOLEAN     NOT NULL DEFAULT false,
    required_action             TEXT        CHECK (required_action IN ('change-password', 'enroll-second-factor')),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    authenticated_at            TIMESTAMPTZ NOT NULL,
    last_activity_at            TIMESTAMPTZ NOT NULL,
    absolute_expires_at         TIMESTAMPTZ NOT NULL,
    client_ip                   INET,
    user_agent                  TEXT,
    idp_refresh_token_encrypted TEXT,
    idp_claims_evaluated_at     TIMESTAMPTZ,
    idp_groups_claim_status     TEXT        CHECK (idp_groups_claim_status IN ('present', 'missing', 'truncated')),
    revoked_at                  TIMESTAMPTZ,
    revoked_reason              TEXT        CHECK (revoked_reason IN ('logout', 'person-status-change', 'role-change',
                                            'credential-revoked', 'password-reset', 'factor-reset', 'refresh-token-reuse',
                                            'idp-refresh-failed', 'admin-action', 'expired')),
    revoked_by_person_id        BIGINT      REFERENCES persons (id),
    revoked_by_admin_id         UUID        REFERENCES system_admin_accounts (id),
    CONSTRAINT sessions_one_principal CHECK ((person_id IS NULL) <> (system_admin_account_id IS NULL)),
    CONSTRAINT sessions_oidc_provider CHECK ((auth_method = 'oidc') = (auth_provider_id IS NOT NULL))
);

CREATE TABLE totp_credentials (
    credential_id       UUID     PRIMARY KEY REFERENCES local_credentials (id),
    secret_encrypted    TEXT     NOT NULL,
    key_id              TEXT     NOT NULL,
    algorithm           TEXT     NOT NULL DEFAULT 'SHA1' CHECK (algorithm IN ('SHA1', 'SHA256', 'SHA512')),
    digits              SMALLINT NOT NULL DEFAULT 6 CHECK (digits IN (6, 8)),
    period_seconds      SMALLINT NOT NULL DEFAULT 30 CHECK (period_seconds > 0),
    last_used_time_step BIGINT,
    label               TEXT
);

CREATE TABLE login_transactions (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash              BYTEA       NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    system_admin_account_id UUID        NOT NULL REFERENCES system_admin_accounts (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    failed_codes            SMALLINT    NOT NULL DEFAULT 0
);

CREATE TABLE auth_throttle (
    throttle_key      TEXT        PRIMARY KEY,
    failures          INTEGER     NOT NULL DEFAULT 0,
    window_started_at TIMESTAMPTZ NOT NULL,
    locked_until      TIMESTAMPTZ
);

-- Credential status 'pending': TOTP enrollment not yet confirmed
ALTER TABLE local_credentials DROP CONSTRAINT local_credentials_status_check;
ALTER TABLE local_credentials ADD CONSTRAINT local_credentials_status_check
    CHECK (status IN ('pending', 'active', 'revoked'));

-- Setup phase (ADR-042)
ALTER TABLE system_settings
    ADD COLUMN setup_phase_ended_at        TIMESTAMPTZ,
    ADD COLUMN setup_phase_ended_by_action TEXT CHECK (setup_phase_ended_by_action IN
        ('identity-binding', 'idp-group-mapping', 'role-assignment', 'person-credential', 'service-account')),
    ADD CONSTRAINT system_settings_setup_phase CHECK ((setup_phase_ended_at IS NULL) = (setup_phase_ended_by_action IS NULL));

CREATE FUNCTION system_settings_keep_setup_phase_end() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_catalog
AS $$
BEGIN
    IF OLD.setup_phase_ended_at IS NOT NULL AND NEW.setup_phase_ended_at IS DISTINCT FROM OLD.setup_phase_ended_at THEN
        RAISE EXCEPTION 'the end of the setup phase is irreversible (ADR-042)';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER system_settings_keep_setup_phase_end BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION system_settings_keep_setup_phase_end();

-- Permission catalog and built-in roles, generated from x-oea-permissions and x-oea-builtin-roles
-- of api/openapi.yaml (ADR-032 §3/§4, US-143); a test keeps both in sync.
INSERT INTO permissions (id, category, admin_class, sort_order) VALUES
    ('portal:read', 'portal', false, 0),
    ('entity:read', 'entity', false, 1),
    ('entity:write', 'entity', false, 2),
    ('entity:delete', 'entity', false, 3),
    ('entity:restore', 'entity', false, 4),
    ('catalog:manage', 'catalog', false, 5),
    ('dashboard:manage', 'dashboard', false, 6),
    ('viewpoint:manage', 'viewpoint', false, 7),
    ('plateau:go-live', 'plateau', false, 8),
    ('webhook:manage', 'webhook', false, 9),
    ('analytics:query', 'analytics', false, 10),
    ('audit:read', 'audit', false, 11),
    ('metamodel:write', 'metamodel', true, 12),
    ('visibility:configure', 'visibility', true, 13),
    ('visibility-scope:assign', 'visibility-scope', true, 14),
    ('role:assign', 'role', true, 15),
    ('person:manage', 'person', true, 16),
    ('service-account:manage', 'service-account', true, 17),
    ('auth-config:manage', 'auth-config', true, 18),
    ('settings:manage', 'settings', true, 19);

INSERT INTO authorization_roles (id, name, is_builtin, assignable_to_service_accounts) VALUES
    ('portal-reader', 'Portal Reader', true, false),
    ('viewer', 'Viewer', true, true),
    ('analyst', 'Analyst', true, false),
    ('enterprise-architect', 'Enterprise Architect', true, false),
    ('integration-writer', 'Integration Writer', true, true),
    ('auditor', 'Auditor', true, true),
    ('metamodel-admin', 'Metamodel Admin', true, false),
    ('security-admin', 'Security Admin', true, false),
    ('system-admin', 'System Admin', true, false);

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('portal-reader', 'portal:read'),
    ('viewer', 'portal:read'),
    ('viewer', 'entity:read'),
    ('analyst', 'portal:read'),
    ('analyst', 'entity:read'),
    ('analyst', 'analytics:query'),
    ('enterprise-architect', 'portal:read'),
    ('enterprise-architect', 'entity:read'),
    ('enterprise-architect', 'entity:write'),
    ('enterprise-architect', 'entity:delete'),
    ('enterprise-architect', 'entity:restore'),
    ('enterprise-architect', 'catalog:manage'),
    ('enterprise-architect', 'dashboard:manage'),
    ('enterprise-architect', 'viewpoint:manage'),
    ('enterprise-architect', 'plateau:go-live'),
    ('enterprise-architect', 'webhook:manage'),
    ('integration-writer', 'entity:read'),
    ('integration-writer', 'entity:write'),
    ('auditor', 'audit:read'),
    ('metamodel-admin', 'metamodel:write'),
    ('security-admin', 'visibility:configure'),
    ('security-admin', 'visibility-scope:assign'),
    ('security-admin', 'role:assign'),
    ('security-admin', 'person:manage'),
    ('system-admin', 'role:assign'),
    ('system-admin', 'person:manage'),
    ('system-admin', 'service-account:manage'),
    ('system-admin', 'auth-config:manage'),
    ('system-admin', 'settings:manage');

REVOKE ALL ON persons, auth_providers, person_identities, permissions, authorization_roles, role_permissions,
    idp_group_mappings, sessions, totp_credentials, login_transactions, auth_throttle FROM PUBLIC;
REVOKE ALL ON FUNCTION system_settings_keep_setup_phase_end() FROM PUBLIC;

-- Runtime role. Persons, roles and mappings are never deleted (status, soft delete);
-- identity bindings are removed when unbinding; transient tables are cleaned up.
GRANT SELECT, INSERT, UPDATE ON persons, auth_providers, authorization_roles, role_permissions, idp_group_mappings,
    sessions, totp_credentials TO oea_app;
GRANT SELECT ON permissions TO oea_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON person_identities, login_transactions, auth_throttle TO oea_app;
GRANT USAGE ON SEQUENCE persons_id_seq TO oea_app;
