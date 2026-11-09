-- Audit value keys (change us-126-role-restricted-enforcement D4; ADR-033 §3, REQ-160 AC4,
-- REQ-188; docs/data-model.puml). Runs as oea_owner. A data key encrypts the old and new values
-- of restricted properties in audit.event_changes; it is stored only wrapped by the
-- key-encryption key. The table is not part of the hash chain: re-wrapping a key after a
-- rotation of the key-encryption key must not touch the chain, and the records are never
-- re-encrypted.

CREATE TABLE audit.value_keys (
    key_id      TEXT        PRIMARY KEY,
    wrapped_key TEXT        NOT NULL,
    kek_id      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

REVOKE ALL ON audit.value_keys FROM PUBLIC;
GRANT SELECT, INSERT ON audit.value_keys TO oea_app;
GRANT UPDATE (wrapped_key, kek_id) ON audit.value_keys TO oea_app;
GRANT SELECT ON audit.value_keys TO oea_auditor;
