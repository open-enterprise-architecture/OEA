-- Service accounts and their client secrets (change us-142-service-accounts D1, D5; US-142,
-- US-149, REQ-155, REQ-176; docs/data-model.puml, business-objects/machine-credential.md).
-- Runs as oea_owner.
--
-- A service account is a person with person_type 'service-account'. Each secret version is a
-- row of machine_credentials: a rotation inserts a row with the same client_id and marks the
-- previous one 'rotated'; at most one row per client_id is active.

ALTER TABLE persons
    ADD COLUMN description TEXT CHECK (char_length(description) <= 1000);

-- names of service accounts are unique, ignoring case; humans may share names
CREATE UNIQUE INDEX persons_service_account_name_unique ON persons (lower(name))
    WHERE person_type = 'service-account';

CREATE TABLE machine_credentials (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id            UUID        NOT NULL,
    person_id            BIGINT      NOT NULL REFERENCES persons (id),
    client_secret_hash   TEXT        NOT NULL,
    status               TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'rotated', 'revoked')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_person_id BIGINT      REFERENCES persons (id),
    created_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    expires_at           TIMESTAMPTZ NOT NULL,
    last_used_at         TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    revoked_by_person_id BIGINT      REFERENCES persons (id),
    revoked_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    CONSTRAINT machine_credentials_expiry CHECK (expires_at > created_at),
    CONSTRAINT machine_credentials_revocation CHECK ((status = 'active') = (revoked_at IS NULL))
);

CREATE UNIQUE INDEX machine_credentials_one_active ON machine_credentials (client_id) WHERE status = 'active';
CREATE INDEX machine_credentials_person ON machine_credentials (person_id);

-- a foreign key cannot check the type of the referenced person
CREATE FUNCTION machine_credentials_require_service_account() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_catalog
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM persons WHERE id = NEW.person_id AND person_type = 'service-account') THEN
        RAISE EXCEPTION 'machine credentials belong to service accounts only (REQ-176)';
    END IF;
    IF EXISTS (SELECT 1 FROM machine_credentials WHERE client_id = NEW.client_id AND person_id <> NEW.person_id) THEN
        RAISE EXCEPTION 'a client_id belongs to one service account';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER machine_credentials_service_account
    BEFORE INSERT OR UPDATE OF person_id, client_id ON machine_credentials
    FOR EACH ROW EXECUTE FUNCTION machine_credentials_require_service_account();

-- rows are never deleted: the rotation history stays auditable
REVOKE ALL ON machine_credentials FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON machine_credentials TO oea_app;
