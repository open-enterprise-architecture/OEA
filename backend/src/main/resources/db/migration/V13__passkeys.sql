-- Passkeys of persons (change us-022-passkeys, design D2; docs/data-model.puml). Runs as
-- oea_owner. A passkey is a local credential of type 'passkey' with the WebAuthn data of
-- WebAuthn4J (ADR-048); challenges of registrations and logins are kept until used or expired.

CREATE TABLE passkey_credentials (
    credential_id            UUID    PRIMARY KEY REFERENCES local_credentials (id),
    -- the credential ID chosen by the authenticator; identifies the passkey at the login
    webauthn_credential_id   BYTEA   NOT NULL UNIQUE,
    -- AAGUID, credential ID and COSE public key (WebAuthn4J AttestedCredentialDataConverter)
    attested_credential_data BYTEA   NOT NULL,
    sign_count               BIGINT  NOT NULL DEFAULT 0 CHECK (sign_count >= 0),
    transports               TEXT[],
    backup_eligible          BOOLEAN NOT NULL,
    backed_up                BOOLEAN NOT NULL,
    label                    TEXT    CHECK (char_length(label) <= 64)
);

-- WebAuthn user handle of a person: 64 random bytes, never the e-mail address or the ID (US-022)
ALTER TABLE persons
    ADD COLUMN webauthn_user_handle BYTEA UNIQUE CHECK (octet_length(webauthn_user_handle) = 64);

CREATE TABLE webauthn_challenges (
    id                   UUID        PRIMARY KEY,
    purpose              TEXT        NOT NULL CHECK (purpose IN ('registration', 'login', 'second-factor')),
    challenge            BYTEA       NOT NULL CHECK (octet_length(challenge) = 32),
    person_id            BIGINT      REFERENCES persons (id),
    login_transaction_id UUID        REFERENCES login_transactions (id) ON DELETE CASCADE,
    label                TEXT        CHECK (char_length(label) <= 64),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT webauthn_challenges_owner CHECK (
        (purpose = 'login' AND person_id IS NULL AND login_transaction_id IS NULL)
        OR (purpose = 'registration' AND person_id IS NOT NULL AND login_transaction_id IS NULL)
        OR (purpose = 'second-factor' AND person_id IS NOT NULL AND login_transaction_id IS NOT NULL))
);

CREATE INDEX webauthn_challenges_expires_at ON webauthn_challenges (expires_at);

REVOKE ALL ON passkey_credentials, webauthn_challenges FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON passkey_credentials TO oea_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON webauthn_challenges TO oea_app;

-- A person's login with password and passkey (design D5)
ALTER TABLE sessions
    DROP CONSTRAINT sessions_auth_method_check,
    ADD CONSTRAINT sessions_auth_method_check
        CHECK (auth_method IN ('oidc', 'passkey', 'password-totp', 'password-passkey', 'password'));
