-- OIDC login of persons (change us-001-oidc-login; docs/data-model.puml). Runs as oea_owner.

-- Group identifiers of the last evaluation (SoD-1, access review) and the expiry of the
-- IdP refresh token, which bounds the session (owner decision 2026-10-15, REQ-001, REQ-165)
ALTER TABLE sessions
    ADD COLUMN idp_group_identifiers TEXT[],
    ADD COLUMN idp_token_expires_at  TIMESTAMPTZ,
    ADD CONSTRAINT sessions_idp_fields_oidc_only CHECK (
        auth_method = 'oidc' OR (idp_group_identifiers IS NULL AND idp_token_expires_at IS NULL
                                 AND idp_refresh_token_encrypted IS NULL));

-- IdP-derived roles per session (ADR-032 DM-10); replaced at every evaluation
CREATE TABLE session_idp_roles (
    session_id           UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    idp_group_mapping_id UUID        NOT NULL REFERENCES idp_group_mappings (id),
    role_id              TEXT        NOT NULL REFERENCES authorization_roles (id),
    derived_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, idp_group_mapping_id)
);

-- Running authorization code flows: state, nonce and PKCE verifier, single use, 10 minutes
CREATE TABLE oidc_login_states (
    state_hash              BYTEA       PRIMARY KEY CHECK (octet_length(state_hash) = 32),
    browser_binding_hash    BYTEA       NOT NULL CHECK (octet_length(browser_binding_hash) = 32),
    auth_provider_id        UUID        NOT NULL REFERENCES auth_providers (id),
    nonce                   TEXT        NOT NULL,
    code_verifier_encrypted TEXT        NOT NULL,
    code_verifier_key_id    TEXT        NOT NULL,
    return_to               TEXT        NOT NULL CHECK (return_to LIKE '/%' AND return_to NOT LIKE '//%'),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL
);

REVOKE ALL ON session_idp_roles, oidc_login_states FROM PUBLIC;

-- Runtime role: both tables are transient per session or per login
GRANT SELECT, INSERT, UPDATE, DELETE ON session_idp_roles, oidc_login_states TO oea_app;
