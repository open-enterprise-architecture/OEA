-- Access tokens of service accounts (change s12-client-credentials-token D2; ADR-050, REQ-176;
-- docs/data-model.puml, business-objects/machine-credential.md). Runs as oea_owner.
--
-- A token is opaque: only its SHA-256 hash is stored, with the client, the secret version that
-- obtained it, its issue time, its expiry 900 seconds later and its revocation. Expired tokens
-- are deleted by the cleanup job one day after their expiry.

CREATE TABLE access_tokens (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash        BYTEA       NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    client_id         UUID        NOT NULL,
    credential_id     UUID        NOT NULL REFERENCES machine_credentials (id),
    issued_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revocation_reason TEXT        CHECK (revocation_reason IN ('rotation', 'deactivation')),
    CONSTRAINT access_tokens_lifetime CHECK (expires_at = issued_at + interval '900 seconds'),
    CONSTRAINT access_tokens_revocation CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL))
);

CREATE INDEX access_tokens_client ON access_tokens (client_id);
CREATE INDEX access_tokens_expiry ON access_tokens (expires_at);

REVOKE ALL ON access_tokens FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON access_tokens TO oea_app;
